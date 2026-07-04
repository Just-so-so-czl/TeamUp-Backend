package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.model.dto.MentorChatHistoryRequest;
import com.czl.teamupbackend.model.dto.MentorChatRequest;
import com.czl.teamupbackend.model.dto.MentorCreateSessionRequest;
import com.czl.teamupbackend.model.dto.MentorSessionListRequest;
import com.czl.teamupbackend.model.entity.AiChatMessageIndex;
import com.czl.teamupbackend.model.entity.AiChatSession;
import com.czl.teamupbackend.model.mongo.MentorChatMessageDoc;
import com.czl.teamupbackend.model.vo.MentorChatHistoryVO;
import com.czl.teamupbackend.model.vo.MentorChatMessageItemVO;
import com.czl.teamupbackend.model.vo.MentorSessionItemVO;
import com.czl.teamupbackend.model.vo.MentorSessionListVO;
import com.czl.teamupbackend.repository.MentorChatMessageRepository;
import com.czl.teamupbackend.service.IAiChatMessageIndexService;
import com.czl.teamupbackend.service.IAiChatSessionService;
import com.czl.teamupbackend.service.IMentorChatService;
import com.czl.teamupbackend.service.MemoryLifecycleService;
import com.czl.teamupbackend.service.ITeamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorChatServiceImpl implements IMentorChatService {

    private static final String EVENT_CHUNK = "chunk";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_META = "meta";
    private static final String EVENT_TITLE = "title";
    private static final String SENDER_USER = "USER";
    private static final String SENDER_ASSISTANT = "ASSISTANT";
    private static final String MSG_TYPE_TEXT = "TEXT";
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_DONE = 2;
    private static final int STATUS_FAILED = 3;
    private static final String DEFAULT_SESSION_TITLE = "智能导师对话";
    private static final String DEFAULT_COLLAB_SESSION_TITLE = "文档助手对话";
    private static final String SESSION_TYPE_TEAM_MENTOR = "TEAM_MENTOR";
    private static final String SESSION_TYPE_COLLAB_DOC = "COLLAB_DOC";
    private static final int SESSION_TITLE_PREFIX_MAX = 18;
    private static final String TOOL_CTX_USER_ID = "userId";
    private static final String TOOL_CTX_TEAM_ID = "teamId";
    private static final String SYSTEM_PROMPT = "你是 TeamUp 平台的智能导师，负责大学生小组协作的助手,解答用户提出的小组协作的问题和小组任务中的知识性问题";
    private static final int HISTORY_WINDOW_MAX_TOKENS = 4000;
    private static final int TITLE_GENERATION_TIMEOUT_SECONDS = 15;
    private static final Duration MESSAGE_TOKEN_CACHE_TTL = Duration.ofDays(7);
    private static final String REDIS_MSG_TOKEN_KEY_PREFIX = "chat:msg:token:";
    private static final String HISTORY_PROMPT_PREFIX = """
        以下是当前会话中按时间顺序截取出的最近历史对话原文，请结合这些上下文继续回答。
        若历史内容与当前提问无关，可按需忽略无关部分，但不要凭空捏造历史事实。

        """;

    private final ChatClient.Builder chatClientBuilder;
    private final IAiChatSessionService aiChatSessionService;
    private final IAiChatMessageIndexService aiChatMessageIndexService;
    private final MentorChatMessageRepository mentorChatMessageRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Executor mvcAsyncTaskExecutor;
    private final TokenCountEstimator tokenCountEstimator;
    private final MemoryLifecycleService memoryLifecycleService;
    private final ITeamService teamService;

    @Value("${spring.ai.openai.summary.model}")
    private String summaryModel;

    @Override
    public SseEmitter streamChat(Long userId, MentorChatRequest request) {
        if (userId == null) {
            throw new BizException(401, "user not login");
        }
        if (request == null || request.getTeamId() == null) {
            throw new BizException(400, "teamId 不能为空");
        }
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty()) {
            throw new BizException(400, "消息内容不能为空");
        }
        teamService.validateTeamAccessible(userId, request.getTeamId());
        validateSessionScope(resolveSessionType(request.getSessionType()), request.getDocumentId());

        AiChatSession session = getOrCreateSession(userId, request);
        boolean shouldGenerateTitle = isFirstRoundSession(session);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        int currentPromptTokenCount = estimateUserPromptTokenCount(message);

        AiChatMessageIndex userMsgIndex = buildMsgIndex(session, userId, SENDER_USER, traceId, STATUS_DONE);
        String userMongoId = createMongoMessage(userMsgIndex.getId(), session, userId, SENDER_USER, message, traceId, now, null);
        userMsgIndex.setMongoMessageId(userMongoId);
        userMsgIndex.setTokenCount(currentPromptTokenCount);
        aiChatMessageIndexService.save(userMsgIndex);
        cacheMessageTokenCount(userMsgIndex.getId(), userMsgIndex.getTokenCount());
        memoryLifecycleService.onShortTermMessageAdded(userMsgIndex);

        AiChatMessageIndex assistantMsgIndex = buildMsgIndex(session, userId, SENDER_ASSISTANT, traceId, STATUS_PENDING);
        String assistantMongoId = createMongoMessage(
            assistantMsgIndex.getId(), session, userId, SENDER_ASSISTANT, "", traceId, now, null);
        assistantMsgIndex.setMongoMessageId(assistantMongoId);
        aiChatMessageIndexService.save(assistantMsgIndex);

        SseEmitter emitter = new SseEmitter(0L);
        Map<String, Object> toolContext = buildToolContext(userId, request.getTeamId(), session.getTeamId());
        String historyPrompt = buildSlidingWindowHistoryPrompt(session.getId(), userMsgIndex.getId());
        mvcAsyncTaskExecutor.execute(() -> doStream(
            emitter, message, historyPrompt,
            session, traceId, assistantMsgIndex, assistantMongoId, toolContext, shouldGenerateTitle));
        return emitter;
    }

    private void doStream(SseEmitter emitter, String message, String historyPrompt,
                          AiChatSession session, String traceId,
                          AiChatMessageIndex assistantMsgIndex, String assistantMongoId,
                          Map<String, Object> toolContext,
                          boolean shouldGenerateTitle) {
        StringBuilder assistantFullText = new StringBuilder();
        UsageUsageHolder usageHolder = new UsageUsageHolder();
        try {
            emitter.send(SseEmitter.event()
                .name(EVENT_META)
                .data("{\"sessionId\":\"" + session.getId() + "\",\"traceId\":\"" + traceId + "\"}"));

            ChatClient chatClient = chatClientBuilder.build();
            OpenAiChatOptions requestOptions = OpenAiChatOptions.builder()
                .streamUsage(true)
                .build();
            ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                .system(buildSystemPrompt(historyPrompt))
                .user(message)
                .options(requestOptions)
                .toolNames("queryTeamOverview", "queryTeamMembers", "queryTeamTaskLists", "queryTeamDocuments")
                .toolContext(toolContext);
            promptSpec.stream()
                .chatResponse()
                .doOnNext(chatResponse -> {
                    captureUsage(usageHolder, chatResponse);
                    String chunk = extractChunkContent(chatResponse);
                    if (chunk == null || chunk.isEmpty()) {
                        return;
                    }
                    assistantFullText.append(chunk);
                    try {
                        log.debug("Mentor stream chunk sessionId={}, traceId={}, length={}",
                            session.getId(), traceId, chunk.length());
                        emitter.send(SseEmitter.event()
                            .name(EVENT_CHUNK)
                            .id(String.valueOf(System.nanoTime()))
                            .data(chunk));
                    }
                    catch (IOException sendError) {
                        throw new RuntimeException(sendError);
                    }
                })
                .doOnComplete(() -> {
                    LocalDateTime endAt = LocalDateTime.now();
                    int assistantTokenCount = resolveCompletionTokens(usageHolder, assistantFullText.toString());
                    updateMongoMessageContent(assistantMongoId, assistantFullText.toString(), endAt);
                    markMsgDone(assistantMsgIndex.getId(), assistantTokenCount);
                    assistantMsgIndex.setStatus(STATUS_DONE);
                    assistantMsgIndex.setTokenCount(assistantTokenCount);
                    memoryLifecycleService.onShortTermMessageAdded(assistantMsgIndex);
                    refreshSessionStats(session.getId(), endAt);
                    cacheRecentContext(session.getId());
                    completeStreamAfterOptionalTitle(
                        emitter,
                        session,
                        message,
                        assistantFullText.toString(),
                        traceId,
                        shouldGenerateTitle
                    );
                })
                .doOnError(ex -> {
                    markMsgFailed(assistantMsgIndex.getId(), ex.getMessage());
                    refreshSessionStats(session.getId(), LocalDateTime.now());
                    log.error("Mentor stream failed, sessionId={}, traceId={}", session.getId(), traceId, ex);
                    emitter.completeWithError(ex);
                })
                .blockLast();
        } catch (Exception e) {
            markMsgFailed(assistantMsgIndex.getId(), e.getMessage());
            refreshSessionStats(session.getId(), LocalDateTime.now());
            log.error("Mentor stream transport failed, sessionId={}, traceId={}", session.getId(), traceId, e);
            emitter.completeWithError(e);
        }
    }

    @Override
    public MentorSessionListVO listSessions(Long userId, MentorSessionListRequest request) {
        if (userId == null) {
            throw new BizException(401, "user not login");
        }
        if (request == null || request.getTeamId() == null) {
            throw new BizException(400, "teamId 不能为空");
        }
        teamService.validateTeamAccessible(userId, request.getTeamId());
        String sessionType = resolveSessionType(request.getSessionType());
        Long documentId = normalizeDocumentId(request.getDocumentId());
        validateSessionScope(sessionType, documentId);
        LambdaQueryWrapper<AiChatSession> queryWrapper = new LambdaQueryWrapper<AiChatSession>()
            .eq(AiChatSession::getCreatorUserId, userId)
            .eq(AiChatSession::getTeamId, request.getTeamId())
            .eq(AiChatSession::getDeleted, 0)
            .eq(AiChatSession::getSessionType, sessionType);
        if (SESSION_TYPE_COLLAB_DOC.equals(sessionType)) {
            queryWrapper.eq(AiChatSession::getDocumentId, documentId);
        } else {
            queryWrapper.isNull(AiChatSession::getDocumentId);
        }
        queryWrapper.orderByDesc(AiChatSession::getLastMessageAt);
        List<AiChatSession> sessions = aiChatSessionService.list(queryWrapper);
        log.info("Mentor session list query done userId={}, teamId={}, sessionType={}, documentId={}, dbSessionCount={}",
            userId, request.getTeamId(), sessionType, documentId, sessions == null ? 0 : sessions.size());
        List<MentorSessionItemVO> items = sessions.stream()
            .map(s -> MentorSessionItemVO.builder()
                .sessionId(String.valueOf(s.getId()))
                .title(s.getTitle())
                .status(s.getStatus() != null && s.getStatus() == 2 ? "CLOSED" : "ACTIVE")
                .messageCount(s.getMessageCount())
                .lastMessageAt(s.getLastMessageAt())
                .build())
            .collect(Collectors.toList());
        log.info("Mentor session list mapped userId={}, teamId={}, responseSessionCount={}, sessionIds={}",
            userId, request.getTeamId(), items.size(), items.stream().map(MentorSessionItemVO::getSessionId).toList());
        return MentorSessionListVO.builder().sessions(items).build();
    }

    @Override
    public MentorChatHistoryVO getHistory(Long userId, MentorChatHistoryRequest request) {
        if (userId == null) {
            throw new BizException(401, "user not login");
        }
        if (request == null || request.getSessionId() == null) {
            throw new BizException(400, "sessionId 不能为空");
        }
        AiChatSession session = aiChatSessionService.getById(request.getSessionId());
        if (session == null || !userId.equals(session.getCreatorUserId())
            || (session.getDeleted() != null && session.getDeleted() == 1)) {
            throw new BizException(403, "会话不存在或无权限");
        }
        List<AiChatMessageIndex> indexes = aiChatMessageIndexService.list(new LambdaQueryWrapper<AiChatMessageIndex>()
            .eq(AiChatMessageIndex::getSessionId, request.getSessionId())
            .eq(AiChatMessageIndex::getDeleted, 0)
            .orderByAsc(AiChatMessageIndex::getCreatedAt));
        log.info("Mentor history query done userId={}, sessionId={}, indexCount={}",
            userId, request.getSessionId(), indexes == null ? 0 : indexes.size());

        List<String> mongoIds = indexes.stream()
            .map(AiChatMessageIndex::getMongoMessageId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toList());
        Map<String, MentorChatMessageDoc> docMap = mentorChatMessageRepository.findByIdIn(mongoIds).stream()
            .collect(Collectors.toMap(MentorChatMessageDoc::getId, d -> d, (a, b) -> a));

        List<MentorChatMessageItemVO> messages = indexes.stream()
            .map(i -> {
                MentorChatMessageDoc doc = docMap.get(i.getMongoMessageId());
                return MentorChatMessageItemVO.builder()
                    .messageId(String.valueOf(i.getId()))
                    .senderType(i.getSenderType())
                    .messageType(i.getMessageType())
                    .content(doc == null ? "" : doc.getContent())
                    .createdAt(i.getCreatedAt())
                    .build();
            })
            .collect(Collectors.toList());
        log.info("Mentor history mapped userId={}, sessionId={}, responseMessageCount={}",
            userId, request.getSessionId(), messages.size());

        return MentorChatHistoryVO.builder()
            .sessionId(String.valueOf(request.getSessionId()))
            .messages(messages)
            .build();
    }

    @Override
    public MentorSessionItemVO createSession(Long userId, MentorCreateSessionRequest request) {
        if (userId == null) {
            throw new BizException(401, "user not login");
        }
        if (request == null || request.getTeamId() == null) {
            throw new BizException(400, "teamId 不能为空");
        }
        teamService.validateTeamAccessible(userId, request.getTeamId());
        String sessionType = resolveSessionType(request.getSessionType());
        Long documentId = normalizeDocumentId(request.getDocumentId());
        validateSessionScope(sessionType, documentId);
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty()) {
            title = SESSION_TYPE_COLLAB_DOC.equals(sessionType) ? DEFAULT_COLLAB_SESSION_TITLE : DEFAULT_SESSION_TITLE;
        }
        AiChatSession session = createSessionRecord(userId, request.getTeamId(), title, sessionType, documentId);
        return MentorSessionItemVO.builder()
            .sessionId(String.valueOf(session.getId()))
            .title(session.getTitle())
            .status("ACTIVE")
            .messageCount(0)
            .lastMessageAt(session.getLastMessageAt())
            .build();
    }

    private AiChatSession getOrCreateSession(Long userId, MentorChatRequest request) {
        String sessionType = resolveSessionType(request.getSessionType());
        Long documentId = normalizeDocumentId(request.getDocumentId());
        if (request.getSessionId() != null) {
            AiChatSession exist = aiChatSessionService.getById(request.getSessionId());
            if (exist == null
                || !request.getTeamId().equals(exist.getTeamId())
                || !userId.equals(exist.getCreatorUserId())
                || !sessionType.equals(resolveSessionType(exist.getSessionType()))
                || !documentIdMatches(documentId, exist.getDocumentId())) {
                log.warn("Ignore invalid mentor sessionId={}, userId={}, teamId={}, sessionType={}, documentId={}",
                    request.getSessionId(), userId, request.getTeamId(), sessionType, documentId);
                request.setSessionId(null);
                return getOrCreateSession(userId, request);
            }
            log.info("Mentor reuse session userId={}, teamId={}, sessionId={}, sessionType={}, documentId={}",
                userId, request.getTeamId(), request.getSessionId(), sessionType, documentId);
            return exist;
        }
        String title = SESSION_TYPE_COLLAB_DOC.equals(sessionType) ? DEFAULT_COLLAB_SESSION_TITLE : DEFAULT_SESSION_TITLE;
        return createSessionRecord(userId, request.getTeamId(), title, sessionType, documentId);
    }

    private AiChatSession createSessionRecord(Long userId, Long teamId, String title, String sessionType, Long documentId) {
        AiChatSession session = new AiChatSession()
            .setTeamId(teamId)
            .setCreatorUserId(userId)
            .setTitle(title)
            .setSessionType(sessionType)
            .setDocumentId(documentId)
            .setStatus(1)
            .setLastMessageAt(LocalDateTime.now())
            .setMessageCount(0)
            .setDeleted(0);
        boolean saved = aiChatSessionService.save(session);
        log.info("Mentor create session result saved={}, userId={}, teamId={}, sessionId={}, sessionType={}, documentId={}",
            saved, userId, teamId, session.getId(), sessionType, documentId);
        return session;
    }

    private String resolveSessionType(String sessionType) {
        if (sessionType == null || sessionType.isBlank()) {
            return SESSION_TYPE_TEAM_MENTOR;
        }
        String normalized = sessionType.trim().toUpperCase();
        if (SESSION_TYPE_COLLAB_DOC.equals(normalized)) {
            return SESSION_TYPE_COLLAB_DOC;
        }
        return SESSION_TYPE_TEAM_MENTOR;
    }

    private Long normalizeDocumentId(Long documentId) {
        return documentId == null || documentId <= 0 ? null : documentId;
    }

    private void validateSessionScope(String sessionType, Long documentId) {
        if (SESSION_TYPE_COLLAB_DOC.equals(sessionType) && documentId == null) {
            throw new BizException(400, "协作文档助手会话缺少documentId");
        }
    }

    private boolean documentIdMatches(Long requestDocumentId, Long sessionDocumentId) {
        Long normalizedSessionDocumentId = normalizeDocumentId(sessionDocumentId);
        if (requestDocumentId == null) {
            return normalizedSessionDocumentId == null;
        }
        return requestDocumentId.equals(normalizedSessionDocumentId);
    }

    private boolean isFirstRoundSession(AiChatSession session) {
        return session != null && (session.getMessageCount() == null || session.getMessageCount() == 0);
    }

    private void completeStreamAfterOptionalTitle(
        SseEmitter emitter,
        AiChatSession session,
        String userQuestion,
        String assistantAnswer,
        String traceId,
        boolean shouldGenerateTitle
    ) {
        if (!shouldGenerateTitle) {
            sendDoneAndComplete(emitter);
            return;
        }

        CompletableFuture
            .supplyAsync(() -> generateSessionTitle(userQuestion, assistantAnswer), mvcAsyncTaskExecutor)
            .orTimeout(TITLE_GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete((title, ex) -> {
                try {
                    if (ex != null) {
                        log.warn("Generate mentor session title failed, sessionId={}, traceId={}",
                            session.getId(), traceId, ex);
                        return;
                    }
                    if (title == null || title.isBlank()) {
                        return;
                    }
                    updateSessionTitle(session, title);
                    sendTitleEvent(emitter, session.getId(), title, traceId);
                } finally {
                    sendDoneAndComplete(emitter);
                }
            });
    }

    private String generateSessionTitle(String userQuestion, String assistantAnswer) {
        String prompt = """
            你是 TeamUp 的会话标题生成器。请根据第一轮用户问题和 AI 回复，为这段对话生成一个简短中文标题。
            要求：
            1. 只输出标题，不要解释，不要加引号。
            2. 标题不超过18个中文字符。
            3. 保留具体主题，避免“问题讨论”“智能助手”等泛化标题。

            用户问题：
            %s

            AI回复：
            %s
            """.formatted(
            userQuestion == null ? "" : userQuestion,
            assistantAnswer == null ? "" : assistantAnswer
        );
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(summaryModel)
            .temperature(0.2)
            .build();
        String title = chatClientBuilder.build()
            .prompt()
            .user(prompt)
            .options(options)
            .call()
            .content();
        return normalizeGeneratedTitle(title);
    }

    private String normalizeGeneratedTitle(String title) {
        if (title == null) {
            return "";
        }
        String normalized = title
            .replaceAll("[\\r\\n]+", " ")
            .replaceAll("^[\"'“”‘’《》]+|[\"'“”‘’《》]+$", "")
            .replaceAll("^标题[:：]\\s*", "")
            .trim();
        if (normalized.length() <= SESSION_TITLE_PREFIX_MAX) {
            return normalized;
        }
        return normalized.substring(0, SESSION_TITLE_PREFIX_MAX);
    }

    private void updateSessionTitle(AiChatSession session, String title) {
        AiChatSession update = new AiChatSession();
        update.setId(session.getId());
        update.setTitle(title);
        aiChatSessionService.updateById(update);
        session.setTitle(title);
    }

    private void sendTitleEvent(SseEmitter emitter, Long sessionId, String title, String traceId) {
        try {
            Map<String, Object> payload = new HashMap<>(4);
            payload.put("sessionId", String.valueOf(sessionId));
            payload.put("title", title);
            payload.put("traceId", traceId);
            emitter.send(SseEmitter.event()
                .name(EVENT_TITLE)
                .data(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("Send mentor session title event failed, sessionId={}, traceId={}", sessionId, traceId, e);
        }
    }

    private void sendDoneAndComplete(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_DONE).data("[DONE]"));
        } catch (IOException ignored) {
            // Client may close the connection after receiving the last chunk.
        }
        emitter.complete();
    }

    private void updateSessionTitleByFirstQuestion(AiChatSession session, String firstQuestion) {
        if (session == null || firstQuestion == null || firstQuestion.isBlank()) {
            return;
        }
        String currentTitle = session.getTitle() == null ? "" : session.getTitle().trim();
        boolean isDefaultTitle = currentTitle.isEmpty()
            || DEFAULT_SESSION_TITLE.equals(currentTitle)
            || currentTitle.contains("鏅鸿兘瀵煎笀瀵硅瘽");
        boolean isFirstRound = session.getMessageCount() == null || session.getMessageCount() == 0;
        if (!isDefaultTitle || !isFirstRound) {
            return;
        }

        String computedTitle = buildSessionTitleFromQuestion(firstQuestion);
        AiChatSession update = new AiChatSession();
        update.setId(session.getId());
        update.setTitle(computedTitle);
        aiChatSessionService.updateById(update);
        session.setTitle(computedTitle);
    }

    private String buildSessionTitleFromQuestion(String question) {
        String normalized = question.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= SESSION_TITLE_PREFIX_MAX) {
            return normalized;
        }
        return normalized.substring(0, SESSION_TITLE_PREFIX_MAX) + "...";
    }

    private Map<String, Object> buildToolContext(Long userId, Long requestTeamId, Long sessionTeamId) {
        Map<String, Object> context = new HashMap<>(4);
        if (userId != null) {
            context.put(TOOL_CTX_USER_ID, userId);
        }
        Long resolvedTeamId = requestTeamId != null ? requestTeamId : sessionTeamId;
        if (resolvedTeamId != null) {
            context.put(TOOL_CTX_TEAM_ID, resolvedTeamId);
        }
        return context;
    }

    private AiChatMessageIndex buildMsgIndex(AiChatSession session, Long userId, String senderType, String traceId, Integer status) {
        return new AiChatMessageIndex()
            .setSessionId(session.getId())
            .setTeamId(session.getTeamId())
            .setUserId(userId)
            .setSenderType(senderType)
            .setMessageType(MSG_TYPE_TEXT)
            .setTraceId(traceId)
            .setTokenCount(0)
            .setShortTermActive(1)
            .setStatus(status)
            .setErrorMsg("")
            .setDeleted(0);
    }

    private String createMongoMessage(Long messageIndexId, AiChatSession session, Long userId, String senderType,
                                      String content, String traceId, LocalDateTime at, Map<String, Object> payload) {
        MentorChatMessageDoc doc = new MentorChatMessageDoc();
        doc.setMessageIndexId(messageIndexId);
        doc.setSessionId(session.getId());
        doc.setTeamId(session.getTeamId());
        doc.setUserId(userId);
        doc.setSenderType(senderType);
        doc.setMessageType(MSG_TYPE_TEXT);
        doc.setContent(content);
        doc.setStructuredPayload(payload == null ? Map.of() : payload);
        doc.setTraceId(traceId);
        doc.setCreatedAt(at);
        MentorChatMessageDoc saved = mentorChatMessageRepository.save(doc);
        return saved.getId();
    }

    private void updateMongoMessageContent(String mongoId, String content, LocalDateTime createdAt) {
        Optional<MentorChatMessageDoc> optional = mentorChatMessageRepository.findById(mongoId);
        if (optional.isEmpty()) {
            return;
        }
        MentorChatMessageDoc doc = optional.get();
        doc.setContent(content);
        doc.setCreatedAt(createdAt);
        mentorChatMessageRepository.save(doc);
    }

    private void markMsgDone(Long msgId, int tokenCount) {
        AiChatMessageIndex update = new AiChatMessageIndex();
        update.setId(msgId);
        update.setStatus(STATUS_DONE);
        update.setTokenCount(tokenCount);
        update.setErrorMsg("");
        aiChatMessageIndexService.updateById(update);
        cacheMessageTokenCount(msgId, tokenCount);
    }

    private void markMsgFailed(Long msgId, String errMsg) {
        AiChatMessageIndex update = new AiChatMessageIndex();
        update.setId(msgId);
        update.setStatus(STATUS_FAILED);
        update.setErrorMsg(errMsg == null ? "stream error" : errMsg);
        aiChatMessageIndexService.updateById(update);
    }

    private void refreshSessionStats(Long sessionId, LocalDateTime lastMessageAt) {
        long count = aiChatMessageIndexService.count(new LambdaQueryWrapper<AiChatMessageIndex>()
            .eq(AiChatMessageIndex::getSessionId, sessionId)
            .eq(AiChatMessageIndex::getDeleted, 0));
        AiChatSession update = new AiChatSession();
        update.setId(sessionId);
        update.setLastMessageAt(lastMessageAt);
        update.setMessageCount((int) count);
        aiChatSessionService.updateById(update);
    }

    private void cacheRecentContext(Long sessionId) {
        List<MentorChatMessageDoc> docs = mentorChatMessageRepository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId);
        if (docs == null || docs.isEmpty()) {
            return;
        }
        List<MentorChatMessageDoc> sorted = new ArrayList<>(docs);
        sorted.sort(Comparator.comparing(MentorChatMessageDoc::getCreatedAt));
        List<ContextMessage> contextMessages = new ArrayList<>(sorted.size());
        for (MentorChatMessageDoc doc : sorted) {
            String role = SENDER_ASSISTANT.equals(doc.getSenderType()) ? "assistant" : "user";
            contextMessages.add(new ContextMessage(role, doc.getContent() == null ? "" : doc.getContent(), doc.getCreatedAt()));
        }
        try {
            String json = objectMapper.writeValueAsString(contextMessages);
            stringRedisTemplate.opsForValue().set("chat:ctx:" + sessionId, json, Duration.ofHours(2));
        } catch (Exception e) {
            log.warn("cache chat context as json failed, sessionId={}", sessionId, e);
        }
    }

    private String buildSystemPrompt(String historyPrompt) {
        if (historyPrompt == null || historyPrompt.isBlank()) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + "\n\n" + historyPrompt;
    }

    private String buildSlidingWindowHistoryPrompt(Long sessionId, Long excludedMessageId) {
        List<AiChatMessageIndex> indexes = aiChatMessageIndexService.list(new LambdaQueryWrapper<AiChatMessageIndex>()
            .eq(AiChatMessageIndex::getSessionId, sessionId)
            .eq(AiChatMessageIndex::getDeleted, 0)
            .eq(AiChatMessageIndex::getStatus, STATUS_DONE)
            .orderByAsc(AiChatMessageIndex::getCreatedAt));
        if (indexes == null || indexes.isEmpty()) {
            return "";
        }

        List<AiChatMessageIndex> filteredIndexes = indexes.stream()
            .filter(index -> excludedMessageId == null || !excludedMessageId.equals(index.getId()))
            .filter(index -> index.getMongoMessageId() != null && !index.getMongoMessageId().isBlank())
            .toList();
        if (filteredIndexes.isEmpty()) {
            return "";
        }

        List<String> mongoIds = filteredIndexes.stream()
            .map(AiChatMessageIndex::getMongoMessageId)
            .toList();
        Map<String, MentorChatMessageDoc> docMap = mentorChatMessageRepository.findByIdIn(mongoIds).stream()
            .collect(Collectors.toMap(MentorChatMessageDoc::getId, doc -> doc, (left, right) -> left, LinkedHashMap::new));

        int totalTokens = 0;
        List<AiChatMessageIndex> selected = new ArrayList<>();
        for (int i = filteredIndexes.size() - 1; i >= 0; i--) {
            AiChatMessageIndex index = filteredIndexes.get(i);
            MentorChatMessageDoc doc = docMap.get(index.getMongoMessageId());
            if (doc == null) {
                continue;
            }
            int tokenCount = resolveMessageTokenCount(index, doc);
            if (!selected.isEmpty() && totalTokens + tokenCount > HISTORY_WINDOW_MAX_TOKENS) {
                continue;
            }
            if (selected.isEmpty() && tokenCount > HISTORY_WINDOW_MAX_TOKENS) {
                selected.add(index);
                break;
            }
            if (totalTokens + tokenCount > HISTORY_WINDOW_MAX_TOKENS) {
                break;
            }
            selected.add(index);
            totalTokens += tokenCount;
        }
        if (selected.isEmpty()) {
            return "";
        }

        selected.sort(Comparator.comparing(AiChatMessageIndex::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        StringBuilder builder = new StringBuilder(HISTORY_PROMPT_PREFIX);
        for (AiChatMessageIndex index : selected) {
            MentorChatMessageDoc doc = docMap.get(index.getMongoMessageId());
            if (doc == null) {
                continue;
            }
            String role = SENDER_ASSISTANT.equals(index.getSenderType()) ? "导师" : "用户";
            builder.append(role)
                .append("：")
                .append(doc.getContent() == null ? "" : doc.getContent().trim())
                .append('\n');
        }
        return builder.toString().trim();
    }

    private int resolveMessageTokenCount(AiChatMessageIndex index, MentorChatMessageDoc doc) {
        Integer cached = readMessageTokenCountFromRedis(index.getId());
        if (cached != null) {
            return cached;
        }
        Integer dbValue = index.getTokenCount();
        if (dbValue != null && dbValue > 0) {
            cacheMessageTokenCount(index.getId(), dbValue);
            return dbValue;
        }
        int estimated = estimateTokenCount(doc == null ? "" : doc.getContent());
        AiChatMessageIndex update = new AiChatMessageIndex();
        update.setId(index.getId());
        update.setTokenCount(estimated);
        aiChatMessageIndexService.updateById(update);
        cacheMessageTokenCount(index.getId(), estimated);
        index.setTokenCount(estimated);
        return estimated;
    }

    private Integer readMessageTokenCountFromRedis(Long messageId) {
        if (messageId == null) {
            return null;
        }
        String raw = stringRedisTemplate.opsForValue().get(buildMessageTokenKey(messageId));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            TokenCachePayload payload = objectMapper.readValue(raw, TokenCachePayload.class);
            return payload.getTokenCount();
        } catch (Exception e) {
            log.warn("read message token cache failed, messageId={}", messageId, e);
            return null;
        }
    }

    private void cacheMessageTokenCount(Long messageId, Integer tokenCount) {
        if (messageId == null || tokenCount == null || tokenCount < 0) {
            return;
        }
        try {
            String raw = objectMapper.writeValueAsString(new TokenCachePayload(tokenCount, LocalDateTime.now()));
            stringRedisTemplate.opsForValue().set(buildMessageTokenKey(messageId), raw, MESSAGE_TOKEN_CACHE_TTL);
        } catch (Exception e) {
            log.warn("cache message token failed, messageId={}, tokenCount={}", messageId, tokenCount, e);
        }
    }

    private String buildMessageTokenKey(Long messageId) {
        return REDIS_MSG_TOKEN_KEY_PREFIX + messageId;
    }

    private int estimateUserPromptTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, tokenCountEstimator.estimate(content));
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        int chineseChars = 0;
        int asciiChars = 0;
        int otherChars = 0;
        for (char ch : content.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (isChinese(ch)) {
                chineseChars++;
            } else if (ch < 128) {
                asciiChars++;
            } else {
                otherChars++;
            }
        }
        int asciiTokens = (int) Math.ceil(asciiChars / 4.0d);
        int otherTokens = (int) Math.ceil(otherChars / 2.0d);
        return Math.max(1, chineseChars + asciiTokens + otherTokens);
    }

    private boolean isChinese(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
            || block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }

    private void captureUsage(UsageUsageHolder usageHolder, ChatResponse chatResponse) {
        if (usageHolder == null || chatResponse == null || chatResponse.getMetadata() == null) {
            return;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        if (usage.getPromptTokens() != null) {
            usageHolder.setPromptTokens(usage.getPromptTokens());
        }
        if (usage.getCompletionTokens() != null) {
            usageHolder.setCompletionTokens(usage.getCompletionTokens());
        }
    }

    private String extractChunkContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private int resolveCompletionTokens(UsageUsageHolder usageHolder, String assistantText) {
        if (usageHolder != null && usageHolder.getCompletionTokens() != null && usageHolder.getCompletionTokens() > 0) {
            return usageHolder.getCompletionTokens();
        }
        return estimateTokenCount(assistantText);
    }

    @Data
    @AllArgsConstructor
    private static class ContextMessage {
        private String role;
        private String content;
        private LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    private static class TokenCachePayload {
        private Integer tokenCount;
        private LocalDateTime updatedAt;
    }

    @Data
    private static class UsageUsageHolder {
        private Integer completionTokens;
        private Integer promptTokens;
    }
}
