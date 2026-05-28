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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
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
    private static final String SENDER_USER = "USER";
    private static final String SENDER_ASSISTANT = "ASSISTANT";
    private static final String MSG_TYPE_TEXT = "TEXT";
    private static final int STATUS_PENDING = 1;
    private static final int STATUS_DONE = 2;
    private static final int STATUS_FAILED = 3;
    private static final String DEFAULT_SESSION_TITLE = "智能导师对话";
    private static final int SESSION_TITLE_PREFIX_MAX = 18;
    private static final String SYSTEM_PROMPT = "你是 TeamUp 平台的智能导师，请给出清晰、可执行、简洁的中文建议。";

    private final ChatClient.Builder chatClientBuilder;
    private final IAiChatSessionService aiChatSessionService;
    private final IAiChatMessageIndexService aiChatMessageIndexService;
    private final MentorChatMessageRepository mentorChatMessageRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final ObjectMapper objectMapper;
    private final Executor mvcAsyncTaskExecutor;

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

        AiChatSession session = getOrCreateSession(userId, request);
        updateSessionTitleByFirstQuestion(session, message);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        String conversationId = String.valueOf(session.getId());

        AiChatMessageIndex userMsgIndex = buildMsgIndex(session, userId, SENDER_USER, traceId, STATUS_DONE);
        String userMongoId = createMongoMessage(userMsgIndex.getId(), session, userId, SENDER_USER, message, traceId, now, null);
        userMsgIndex.setMongoMessageId(userMongoId);
        aiChatMessageIndexService.save(userMsgIndex);

        AiChatMessageIndex assistantMsgIndex = buildMsgIndex(session, userId, SENDER_ASSISTANT, traceId, STATUS_PENDING);
        String assistantMongoId = createMongoMessage(
            assistantMsgIndex.getId(), session, userId, SENDER_ASSISTANT, "", traceId, now, null);
        assistantMsgIndex.setMongoMessageId(assistantMongoId);
        aiChatMessageIndexService.save(assistantMsgIndex);

        SseEmitter emitter = new SseEmitter(0L);
        mvcAsyncTaskExecutor.execute(() -> doStream(
            emitter, message, session, traceId, conversationId, assistantMsgIndex, assistantMongoId));
        return emitter;
    }

    private void doStream(SseEmitter emitter, String message, AiChatSession session, String traceId,
                          String conversationId, AiChatMessageIndex assistantMsgIndex, String assistantMongoId) {
        StringBuilder assistantFullText = new StringBuilder();
        try {
            emitter.send(SseEmitter.event()
                .name(EVENT_META)
                .data("{\"sessionId\":\"" + session.getId() + "\",\"traceId\":\"" + traceId + "\"}"));

            ChatClient chatClient = chatClientBuilder.build();
            chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .advisors(messageChatMemoryAdvisor)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(chunk -> {
                    assistantFullText.append(chunk);
                    try {
                        log.debug("Mentor stream chunk sessionId={}, traceId={}, length={}",
                            session.getId(), traceId, chunk.length());
                        emitter.send(SseEmitter.event()
                            .name(EVENT_CHUNK)
                            .id(String.valueOf(System.nanoTime()))
                            .data(chunk));
                    } catch (IOException sendError) {
                        throw new RuntimeException(sendError);
                    }
                })
                .doOnComplete(() -> {
                    LocalDateTime endAt = LocalDateTime.now();
                    updateMongoMessageContent(assistantMongoId, assistantFullText.toString(), endAt);
                    markMsgDone(assistantMsgIndex.getId());
                    refreshSessionStats(session.getId(), endAt);
                    cacheRecentContext(session.getId());
                    try {
                        emitter.send(SseEmitter.event().name(EVENT_DONE).data("[DONE]"));
                    } catch (IOException ignored) {
                        // Client may close the connection after receiving the last chunk.
                    }
                    emitter.complete();
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
        List<AiChatSession> sessions = aiChatSessionService.list(new LambdaQueryWrapper<AiChatSession>()
            .eq(AiChatSession::getCreatorUserId, userId)
            .eq(AiChatSession::getTeamId, request.getTeamId())
            .eq(AiChatSession::getDeleted, 0)
            .orderByDesc(AiChatSession::getLastMessageAt));
        log.info("Mentor session list query done userId={}, teamId={}, dbSessionCount={}",
            userId, request.getTeamId(), sessions == null ? 0 : sessions.size());
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
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty()) {
            title = DEFAULT_SESSION_TITLE;
        }
        AiChatSession session = createSessionRecord(userId, request.getTeamId(), title);
        return MentorSessionItemVO.builder()
            .sessionId(String.valueOf(session.getId()))
            .title(session.getTitle())
            .status("ACTIVE")
            .messageCount(0)
            .lastMessageAt(session.getLastMessageAt())
            .build();
    }

    private AiChatSession getOrCreateSession(Long userId, MentorChatRequest request) {
        if (request.getSessionId() != null) {
            AiChatSession exist = aiChatSessionService.getById(request.getSessionId());
            if (exist == null || !request.getTeamId().equals(exist.getTeamId()) || !userId.equals(exist.getCreatorUserId())) {
                log.warn("Ignore invalid mentor sessionId={}, userId={}, teamId={}",
                    request.getSessionId(), userId, request.getTeamId());
                request.setSessionId(null);
                return getOrCreateSession(userId, request);
            }
            log.info("Mentor reuse session userId={}, teamId={}, sessionId={}",
                userId, request.getTeamId(), request.getSessionId());
            return exist;
        }
        return createSessionRecord(userId, request.getTeamId(), DEFAULT_SESSION_TITLE);
    }

    private AiChatSession createSessionRecord(Long userId, Long teamId, String title) {
        AiChatSession session = new AiChatSession()
            .setTeamId(teamId)
            .setCreatorUserId(userId)
            .setTitle(title)
            .setStatus(1)
            .setLastMessageAt(LocalDateTime.now())
            .setMessageCount(0)
            .setDeleted(0);
        boolean saved = aiChatSessionService.save(session);
        log.info("Mentor create session result saved={}, userId={}, teamId={}, sessionId={}",
            saved, userId, teamId, session.getId());
        return session;
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

    private AiChatMessageIndex buildMsgIndex(AiChatSession session, Long userId, String senderType, String traceId, Integer status) {
        return new AiChatMessageIndex()
            .setSessionId(session.getId())
            .setTeamId(session.getTeamId())
            .setUserId(userId)
            .setSenderType(senderType)
            .setMessageType(MSG_TYPE_TEXT)
            .setTraceId(traceId)
            .setTokenInput(0)
            .setTokenOutput(0)
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

    private void markMsgDone(Long msgId) {
        AiChatMessageIndex update = new AiChatMessageIndex();
        update.setId(msgId);
        update.setStatus(STATUS_DONE);
        update.setErrorMsg("");
        aiChatMessageIndexService.updateById(update);
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

    @Data
    @AllArgsConstructor
    private static class ContextMessage {
        private String role;
        private String content;
        private LocalDateTime createdAt;
    }
}
