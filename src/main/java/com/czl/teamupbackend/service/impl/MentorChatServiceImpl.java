package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.model.dto.MentorChatHistoryRequest;
import com.czl.teamupbackend.model.dto.MentorChatRequest;
import com.czl.teamupbackend.model.dto.MentorCreateSessionRequest;
import com.czl.teamupbackend.model.dto.MentorSessionListRequest;
import com.czl.teamupbackend.model.entity.AiChatMessageIndex;
import com.czl.teamupbackend.model.entity.AiChatSession;
import com.czl.teamupbackend.model.entity.AiAgentRun;
import com.czl.teamupbackend.model.entity.AiAgentStep;
import com.czl.teamupbackend.model.mongo.MentorChatMessageDoc;
import com.czl.teamupbackend.model.vo.MentorChatHistoryVO;
import com.czl.teamupbackend.model.vo.MentorChatMessageItemVO;
import com.czl.teamupbackend.model.vo.MentorAgentStepVO;
import com.czl.teamupbackend.model.vo.AgentEmailProposalVO;
import com.czl.teamupbackend.model.vo.AgentTaskListProposalVO;
import com.czl.teamupbackend.model.vo.AgentCollaborationDocumentPatchProposalVO;
import com.czl.teamupbackend.model.vo.MentorSessionItemVO;
import com.czl.teamupbackend.model.vo.MentorSessionListVO;
import com.czl.teamupbackend.repository.MentorChatMessageRepository;
import com.czl.teamupbackend.mapper.AiAgentRunMapper;
import com.czl.teamupbackend.mapper.AiAgentStepMapper;
import com.czl.teamupbackend.service.IAiChatMessageIndexService;
import com.czl.teamupbackend.service.IAiChatSessionService;
import com.czl.teamupbackend.service.AgentRunService;
import com.czl.teamupbackend.service.IMentorChatService;
import com.czl.teamupbackend.service.TeamRedisCacheService;
import com.czl.teamupbackend.service.MemoryLifecycleService;
import com.czl.teamupbackend.service.ITeamService;
import com.czl.teamupbackend.service.IDocumentService;
import com.czl.teamupbackend.service.AgentEmailProposalService;
import com.czl.teamupbackend.service.AgentTaskListProposalService;
import com.czl.teamupbackend.service.AgentCollaborationDocumentProposalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String EVENT_AGENT_STATUS = "agent-status";
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
    private static final String TOOL_CTX_SESSION_TYPE = "sessionType";
    private static final String TOOL_CTX_DOCUMENT_ID = "documentId";
    private static final String TOOL_CTX_AGENT_RUN_ID = "agentRunId";
    private static final String SYSTEM_PROMPT = """
        你是 TeamUp 平台的智能导师，负责大学生小组协作的助手。
        你运行在受控的自适应 Plan + ReAct 引擎中：先在内部确定最短可行步骤，再直接回答或调用允许的只读工具。
        简单解释应在一次回复中结束；只有需要实时小组数据时才调用工具。
        你的职责是：理解团队协作上下文，帮助成员梳理信息、提出协作建议、识别风险、总结文档和讨论，并生成可供用户确认的草稿。

        当用户提出看似与小组协作无关的问题时,可能来小组上下文,你可以按需查询：
          - 团队基础情况与成员正式角色；
          - 任务、进度、负责人和截止时间；
          - 资料文档、协作文档及其摘要；
          - 团队工作画像，包括成员工作偏好、协作约定、长期规范、重复协作风险、复盘洞察和待协商议题。

          工具使用规则：
          1. 回答涉及团队事实的问题前，优先查询必要工具，不要凭空编造。
          2. 只调用解决当前问题所需的最少工具，避免一次读取全部数据。
          3. 分工、沟通、协作方式、风险预警问题，优先查询团队工作画像。
          4. 任务状态、负责人、截止时间问题，优先查询任务信息。
          5. 文档细节、来源依据或摘要问题，查询文档信息。
          6. 工作画像中 status=SUGGESTED 的内容只是候选协作记忆，不能表述为已确认事实；只有 CONFIRMED 才可作为团队已确认约定。

          行为边界：
          - 你可以提出建议、生成草稿、列出待确认事项。
           - 你不能声称已创建任务、已修改分工、已更新文档或已代表用户作出决定。
           - 涉及业务状态变化时，先说明建议和影响，等待用户确认。
            - 需要向指定组员发送邮件时，必须先在本轮调用 queryTeamMembers；recipientUserId 必须逐字使用该工具结果中的 userId，禁止猜测、按昵称/邮箱推导或复用历史ID。
           - 若 proposeTeamEmail 返回 recipientValid=false，立即调用 queryTeamMembers 重新获取成员，再使用返回的精确 userId 重试一次。
           - 需要创建任务清单时，调用 proposeTaskList，只填写清单标题、描述和子任务描述；不得填写DDL、负责人或人员分配。
           - proposeTaskList 不会创建任务；任务清单提案生成后立即停止后续业务动作，等待用户在界面中编辑并确认。
           - proposeTeamEmail 不会发送邮件；提案生成后立即停止后续业务动作，等待用户在界面中编辑并确认。

          回答要求：
          - 明确区分已确认事实、建议和待确认事项。
          - 信息不足时说明原因，并查询相关信息或向用户提问。
          - 不推断成员人格、能力高低或敏感隐私。
          - 使用简洁、友好、可执行的中文回答。
            """;
    private static final String DOCUMENT_ASSISTANT_SYSTEM_PROMPT = """
        你是 TeamUp 平台当前协作文档的文档助手，只服务于本次会话绑定的协作文档。
        你运行在受控的自适应 Plan + ReAct 引擎中：先在内部确定最短可行步骤，再直接回答或调用允许的只读工具。
        你的首要职责是帮助用户理解、组织、补全、润色、压缩、审查和改进当前文档的内容；可以结合当前用户明确引用的选中文本给出针对性建议。

        小组概况、成员、任务、资料文档、其他协作文档和团队工作画像仅用于理解项目背景、交付要求和协作约定，不能偏离当前文档去处理小组管理、成员管理、邮件沟通或任务创建。

        工具使用规则：
        1. 只有需要真实小组事实、任务要求、资料依据或工作画像时才调用工具，并且只调用解决当前问题所需的最少工具。
        2. 用户询问当前文档的选中文本时，优先依据本轮引用内容回答；信息不足时明确说明并提出需要补充的上下文。
        3. 工作画像中 status=SUGGESTED 的内容只是候选协作记忆，不能表述为已确认事实；只有 CONFIRMED 才可作为团队已确认约定。
        4. 当用户明确要求新增、替换或删除当前文档的顶层标题/段落时，先调用 queryCurrentCollaborationDocument 获取实时快照，再调用 proposeCollaborationDocumentPatch 生成待审核草案。只能使用工具返回的 snapshotId、targetBlockId 和 expectedTextHash；草案不会立即写入文档。
        5. 如果 proposeCollaborationDocumentPatch 返回 proposalCreated=false 且 retryable=true，必须依据 retryInstruction 修正 Patch，并在同一轮重新调用该工具；不得把校验错误直接转述给用户。
        6. 你不能创建任务清单或发送邮件。文档草案应用前不得声称已修改文档；用户确认后才由系统执行。

        回答要求：
        - 以当前文档为中心，给出清晰、简洁、可直接编辑使用的中文内容。
        - 明确区分文档已有事实、你的修改建议和待确认信息。
        - 不推断成员人格、能力高低或敏感隐私。
        """;
    private static final List<String> SHARED_READ_TOOL_NAMES = List.of(
        "queryTeamOverview", "queryTeamMembers", "queryTeamTaskLists", "queryTeamDocuments",
        "queryDocumentFullText", "queryTeamWorkProfile"
    );
    private static final AgentProfile TEAM_MENTOR_PROFILE = new AgentProfile(
        SYSTEM_PROMPT,
        List.of("queryTeamOverview", "queryTeamMembers", "queryTeamTaskLists", "queryTeamDocuments",
            "queryDocumentFullText", "proposeTeamEmail", "proposeTaskList", "queryTeamWorkProfile")
    );
    private static final AgentProfile COLLAB_DOCUMENT_PROFILE = new AgentProfile(
        DOCUMENT_ASSISTANT_SYSTEM_PROMPT,
        List.of("queryTeamOverview", "queryTeamMembers", "queryTeamTaskLists", "queryTeamDocuments",
            "queryDocumentFullText", "queryTeamWorkProfile", "queryCurrentCollaborationDocument",
            "proposeCollaborationDocumentPatch")
    );
    private static final int HISTORY_WINDOW_MAX_TOKENS = 4000;
    private static final int TITLE_GENERATION_TIMEOUT_SECONDS = 15;
    private static final String HISTORY_PROMPT_PREFIX = """
        以下是当前会话中按时间顺序截取出的最近历史对话原文，请结合这些上下文继续回答。
        若历史内容与当前提问无关，可按需忽略无关部分，但不要凭空捏造历史事实。

        """;
    private static final String CONFIRMATION_OBSERVATION_PREFIX = """
        系统观察到：上一项受控操作已由用户确认并执行成功。
        执行结果：%s

        请基于原始用户目标继续完成尚未完成的后续事项。若仍需产生新的写操作，只生成下一份待确认草案，并在草案生成后停止；不要重复已经成功执行的操作。
        """;

    private final ChatClient.Builder chatClientBuilder;
    private final IAiChatSessionService aiChatSessionService;
    private final IAiChatMessageIndexService aiChatMessageIndexService;
    private final MentorChatMessageRepository mentorChatMessageRepository;
    private final TeamRedisCacheService teamRedisCacheService;
    private final ObjectMapper objectMapper;
    private final Executor mvcAsyncTaskExecutor;
    private final TokenCountEstimator tokenCountEstimator;
    private final MemoryLifecycleService memoryLifecycleService;
    private final ITeamService teamService;
    private final AgentRunService agentRunService;
    private final IDocumentService documentService;
    private final AiAgentRunMapper agentRunMapper;
    private final AiAgentStepMapper agentStepMapper;
    private final AgentEmailProposalService agentEmailProposalService;
    private final AgentTaskListProposalService agentTaskListProposalService;
    private final AgentCollaborationDocumentProposalService agentCollaborationDocumentProposalService;

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
        String sessionType = resolveSessionType(request.getSessionType());
        validateSessionScope(sessionType, request.getDocumentId());
        message = appendSelectedTextQuote(message, request, sessionType);
        String mentionedDocumentReference = documentService.buildMentorMentionReference(
            userId, request.getTeamId(), request.getMentionedDocumentIds());
        String storedUserMessage = mentionedDocumentReference.isBlank()
            ? message
            : message + "\n\n> 引用文档：" + mentionedDocumentReference;
        String modelPrompt = storedUserMessage + documentService.buildMentorMentionContext(
            userId, request.getTeamId(), request.getMentionedDocumentIds());

        AiChatSession session = getOrCreateSession(userId, request);
        boolean shouldGenerateTitle = isFirstRoundSession(session);
        String traceId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        // Agent 运行记录可以保留实际请求 Prompt 的 token 估算；消息索引必须只统计 Mongo 正文。
        int modelPromptTokenCount = estimateTokenCount(modelPrompt);
        int userMessageTokenCount = estimateTokenCount(storedUserMessage);

        AiChatMessageIndex userMsgIndex = buildMsgIndex(session, userId, SENDER_USER, traceId, STATUS_DONE);
        String userMongoId = createMongoMessage(
            userMsgIndex.getId(), session, userId, SENDER_USER, storedUserMessage, traceId, now, null);
        userMsgIndex.setMongoMessageId(userMongoId);
        userMsgIndex.setTokenCount(userMessageTokenCount);
        aiChatMessageIndexService.save(userMsgIndex);
        memoryLifecycleService.onShortTermMessageAdded(userMsgIndex);

        AiChatMessageIndex assistantMsgIndex = buildMsgIndex(session, userId, SENDER_ASSISTANT, traceId, STATUS_PENDING);
        String assistantMongoId = createMongoMessage(
            assistantMsgIndex.getId(), session, userId, SENDER_ASSISTANT, "", traceId, now, null);
        assistantMsgIndex.setMongoMessageId(assistantMongoId);
        aiChatMessageIndexService.save(assistantMsgIndex);

        SseEmitter emitter = new SseEmitter(0L);
        AgentProfile profile = resolveAgentProfile(session);
        Map<String, Object> toolContext = buildToolContext(userId, request.getTeamId(), session);
        String historyPrompt = buildSlidingWindowHistoryPrompt(session.getId(), userMsgIndex.getId());
        var agentRun = agentRunService.start(
            session.getId(), session.getTeamId(), userId, traceId, sessionType, storedUserMessage, modelPromptTokenCount);
        toolContext.put(TOOL_CTX_AGENT_RUN_ID, agentRun.getId());
        agentRunService.registerListener(agentRun.getId(), progress -> sendAgentStatus(emitter, progress));
        final String finalUserPrompt = modelPrompt;
        final String finalStoredUserMessage = storedUserMessage;
        mvcAsyncTaskExecutor.execute(() -> doStream(
            emitter, finalUserPrompt, historyPrompt, profile,
            session, traceId, assistantMsgIndex, assistantMongoId, toolContext, shouldGenerateTitle,
            agentRun.getId(), finalStoredUserMessage));
        return emitter;
    }

    @Override
    public void resumeAfterConfirmation(Long runId, Long userId, String toolName, String resultSummary) {
        if (runId == null || userId == null) {
            return;
        }
        AiAgentRun run = agentRunMapper.selectById(runId);
        if (run == null || !userId.equals(run.getUserId())) {
            log.warn("Skip Agent resume because run is unavailable, runId={}, userId={}", runId, userId);
            return;
        }
        AiChatSession session = aiChatSessionService.getById(run.getSessionId());
        if (session == null || !userId.equals(session.getCreatorUserId())) {
            log.warn("Skip Agent resume because session is unavailable, runId={}, sessionId={}", runId, run.getSessionId());
            return;
        }
        teamService.validateTeamAccessible(userId, run.getTeamId());

        // A confirmation continues the original turn, so keep its answer and workflow in one message.
        String traceId = run.getTraceId();
        AiChatMessageIndex assistantMsgIndex = findLatestAssistantMessage(session.getId(), traceId);
        if (assistantMsgIndex == null || assistantMsgIndex.getMongoMessageId() == null) {
            log.warn("Skip Agent resume because original assistant message is unavailable, runId={}, traceId={}", runId, traceId);
            return;
        }

        AgentProfile profile = resolveAgentProfile(session);
        Map<String, Object> toolContext = buildToolContext(userId, run.getTeamId(), session);
        toolContext.put(TOOL_CTX_AGENT_RUN_ID, runId);
        String safeResultSummary = resultSummary == null ? "已按用户确认执行" : resultSummary;
        if (safeResultSummary.length() > 500) {
            safeResultSummary = safeResultSummary.substring(0, 500);
        }
        String observationPrompt = CONFIRMATION_OBSERVATION_PREFIX.formatted(safeResultSummary);
        String historyPrompt = buildSlidingWindowHistoryPrompt(session.getId(), null);
        String systemPrompt = buildSystemPrompt(profile, historyPrompt) + "\n\n" + observationPrompt;
        mvcAsyncTaskExecutor.execute(() -> doResumeStream(
            runId, session, traceId, assistantMsgIndex, assistantMsgIndex.getMongoMessageId(), profile, toolContext, systemPrompt, observationPrompt));
    }

    private void doResumeStream(
        Long runId,
        AiChatSession session,
        String traceId,
        AiChatMessageIndex assistantMsgIndex,
        String assistantMongoId,
        AgentProfile profile,
        Map<String, Object> toolContext,
        String systemPrompt,
        String observationPrompt
    ) {
        StringBuilder assistantFullText = new StringBuilder(loadMongoMessageContent(assistantMongoId));
        UsageUsageHolder usageHolder = new UsageUsageHolder();
        try {
            agentRunService.markPlanning(runId);
            ChatClient.ChatClientRequestSpec promptSpec = chatClientBuilder.build().prompt()
                .system(systemPrompt)
                .user(observationPrompt)
                .options(OpenAiChatOptions.builder().streamUsage(true).build())
                .toolNames(profile.toolNames().toArray(String[]::new))
                .toolContext(toolContext);
            promptSpec.stream().chatResponse()
                .takeWhile(chatResponse -> !agentRunService.isWaitingConfirmation(runId))
                .doOnNext(chatResponse -> {
                    captureUsage(usageHolder, chatResponse);
                    String chunk = extractChunkContent(chatResponse);
                    if (chunk == null || chunk.isEmpty()) {
                        return;
                    }
                    if (assistantFullText.isEmpty()) {
                        agentRunService.markAnswering(runId);
                    }
                    assistantFullText.append(chunk);
                })
                .doOnComplete(() -> finishResumedRun(
                    runId, session, assistantMsgIndex, assistantMongoId, traceId, assistantFullText, usageHolder))
                .doOnError(ex -> failResumedRun(runId, session, assistantMsgIndex, ex))
                .blockLast();
        } catch (Exception e) {
            failResumedRun(runId, session, assistantMsgIndex, e);
        }
    }

    private void finishResumedRun(
        Long runId,
        AiChatSession session,
        AiChatMessageIndex assistantMsgIndex,
        String assistantMongoId,
        String traceId,
        StringBuilder assistantFullText,
        UsageUsageHolder usageHolder
    ) {
        LocalDateTime endAt = LocalDateTime.now();
        String assistantText = assistantFullText.toString();
        int tokenCount = estimateTokenCount(assistantText);
        updateMongoMessageContent(assistantMongoId, assistantText, endAt);
        markMsgDone(assistantMsgIndex.getId(), tokenCount);
        assistantMsgIndex.setStatus(STATUS_DONE);
        assistantMsgIndex.setTokenCount(tokenCount);
        memoryLifecycleService.onShortTermMessageAdded(assistantMsgIndex);
        refreshSessionStats(session.getId(), endAt);
        cacheRecentContext(session.getId());
        AiAgentRun latestRun = agentRunMapper.selectById(runId);
        if (latestRun == null || !"WAITING_CONFIRMATION".equals(latestRun.getStatus())) {
            agentRunService.complete(runId, resolveCompletionTokens(usageHolder, assistantText));
        }
        log.info("Agent resumed after confirmed action, runId={}, traceId={}, waitingConfirmation={}",
            runId, traceId, latestRun != null && "WAITING_CONFIRMATION".equals(latestRun.getStatus()));
    }

    private void failResumedRun(Long runId, AiChatSession session, AiChatMessageIndex assistantMsgIndex, Throwable error) {
        markMsgFailed(assistantMsgIndex.getId(), error.getMessage());
        refreshSessionStats(session.getId(), LocalDateTime.now());
        agentRunService.fail(runId, error.getMessage());
        log.error("Agent resume failed after confirmed action, runId={}", runId, error);
    }

    private String appendSelectedTextQuote(String message, MentorChatRequest request, String sessionType) {
        String selectedText = request.getSelectedText();
        if (selectedText == null || selectedText.isBlank()) {
            return message;
        }
        if (!SESSION_TYPE_COLLAB_DOC.equals(sessionType)) {
            throw new BizException(400, "仅协作文档助手支持引用选中文本");
        }

        String normalizedText = selectedText.replace("\r\n", "\n").replace('\r', '\n');
        Integer startLine = request.getSelectedStartLine();
        Integer endLine = request.getSelectedEndLine();
        String lineRange = buildSelectedLineRange(startLine, endLine);
        String markdownQuote = Arrays.stream(normalizedText.split("\n", -1))
            .map(line -> "> " + line)
            .collect(Collectors.joining("\n"));
        return message + "\n\n> 引用协作文档" + lineRange + "：\n" + markdownQuote;
    }

    private String buildSelectedLineRange(Integer startLine, Integer endLine) {
        if (startLine == null || startLine <= 0) {
            return "内容";
        }
        if (endLine == null || endLine <= startLine) {
            return "第" + startLine + "行";
        }
        return "第" + startLine + "至第" + endLine + "行";
    }

    private void doStream(SseEmitter emitter, String message, String historyPrompt, AgentProfile profile,
                          AiChatSession session, String traceId,
                          AiChatMessageIndex assistantMsgIndex, String assistantMongoId,
                          Map<String, Object> toolContext,
                          boolean shouldGenerateTitle, Long agentRunId, String storedUserMessage) {
        StringBuilder assistantFullText = new StringBuilder();
        UsageUsageHolder usageHolder = new UsageUsageHolder();
        try {
            emitter.send(SseEmitter.event()
                .name(EVENT_META)
                .data("{\"sessionId\":\"" + session.getId() + "\",\"traceId\":\"" + traceId
                    + "\",\"agentRunId\":\"" + agentRunId + "\"}"));
            agentRunService.markPlanning(agentRunId);

            ChatClient chatClient = chatClientBuilder.build();
            OpenAiChatOptions requestOptions = OpenAiChatOptions.builder()
                .streamUsage(true)
                .build();
            ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                .system(buildSystemPrompt(profile, historyPrompt))
                .user(message)
                .options(requestOptions)
                .toolNames(profile.toolNames().toArray(String[]::new))
                .toolContext(toolContext);
            promptSpec.stream()
                .chatResponse()
                .takeWhile(chatResponse -> !agentRunService.isWaitingConfirmation(agentRunId))
                .doOnNext(chatResponse -> {
                    captureUsage(usageHolder, chatResponse);
                    String chunk = extractChunkContent(chatResponse);
                    if (chunk == null || chunk.isEmpty()) {
                        return;
                    }
                    if (assistantFullText.isEmpty()) {
                        agentRunService.markAnswering(agentRunId);
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
                    int completionTokenCount = resolveCompletionTokens(usageHolder, assistantFullText.toString());
                    int assistantMessageTokenCount = estimateTokenCount(assistantFullText.toString());
                    agentRunService.complete(agentRunId, completionTokenCount);
                    agentRunService.unregisterListener(agentRunId);
                    updateMongoMessageContent(assistantMongoId, assistantFullText.toString(), endAt);
                    markMsgDone(assistantMsgIndex.getId(), assistantMessageTokenCount);
                    assistantMsgIndex.setStatus(STATUS_DONE);
                    assistantMsgIndex.setTokenCount(assistantMessageTokenCount);
                    memoryLifecycleService.onShortTermMessageAdded(assistantMsgIndex);
                    refreshSessionStats(session.getId(), endAt);
                    cacheRecentContext(session.getId());
                    completeStreamAfterOptionalTitle(
                        emitter,
                        session,
                        storedUserMessage,
                        assistantFullText.toString(),
                        traceId,
                        shouldGenerateTitle
                    );
                })
                .doOnError(ex -> {
                    teamRedisCacheService.evictChatHistory(session.getId());
                    agentRunService.fail(agentRunId, ex.getMessage());
                    agentRunService.unregisterListener(agentRunId);
                    markMsgFailed(assistantMsgIndex.getId(), ex.getMessage());
                    refreshSessionStats(session.getId(), LocalDateTime.now());
                    log.error("Mentor stream failed, sessionId={}, traceId={}", session.getId(), traceId, ex);
                    emitter.completeWithError(ex);
                })
                .blockLast();
        } catch (Exception e) {
            teamRedisCacheService.evictChatHistory(session.getId());
            agentRunService.fail(agentRunId, e.getMessage());
            agentRunService.unregisterListener(agentRunId);
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
        Map<String, AiAgentRun> runByTraceId = loadAgentRunsByTraceId(indexes);
        Map<Long, List<MentorAgentStepVO>> stepsByRunId = loadAgentStepsByRunId(runByTraceId.values());
        Map<Long, AgentEmailProposalVO> emailProposalsByRunId = agentEmailProposalService.findByRunIds(userId,
            runByTraceId.values().stream().map(AiAgentRun::getId).toList());
        Map<Long, AgentTaskListProposalVO> taskListProposalsByRunId = agentTaskListProposalService.findByRunIds(userId,
            runByTraceId.values().stream().map(AiAgentRun::getId).toList());
        Map<Long, AgentCollaborationDocumentPatchProposalVO> collaborationDocumentProposalsByRunId =
            agentCollaborationDocumentProposalService.findByRunIds(userId,
                runByTraceId.values().stream().map(AiAgentRun::getId).toList());
        Map<String, Long> latestAssistantMessageIdByTraceId = indexes.stream()
            .filter(index -> SENDER_ASSISTANT.equals(index.getSenderType()))
            .filter(index -> index.getTraceId() != null && !index.getTraceId().isBlank())
            .collect(Collectors.toMap(AiChatMessageIndex::getTraceId, AiChatMessageIndex::getId, (older, newer) -> newer));

        List<MentorChatMessageItemVO> messages = indexes.stream()
            // Older runs may have a continuation message. Return only the final assistant
            // message for that trace so one user turn is rendered as one assistant bubble.
            .filter(index -> !SENDER_ASSISTANT.equals(index.getSenderType())
                || index.getTraceId() == null || index.getTraceId().isBlank()
                || index.getId().equals(latestAssistantMessageIdByTraceId.get(index.getTraceId())))
            .map(i -> {
                MentorChatMessageDoc doc = docMap.get(i.getMongoMessageId());
                if (doc != null) {
                    // 历史页面访问时也顺带修复旧版错误 token 统计。
                    resolveMessageTokenCount(i, doc);
                }
                AiAgentRun agentRun = SENDER_ASSISTANT.equals(i.getSenderType())
                    ? runByTraceId.get(i.getTraceId())
                    : null;
                boolean isLatestRunMessage = agentRun != null && i.getId() != null
                    && i.getId().equals(latestAssistantMessageIdByTraceId.get(i.getTraceId()));
                return MentorChatMessageItemVO.builder()
                    .messageId(String.valueOf(i.getId()))
                    .senderType(i.getSenderType())
                    .messageType(i.getMessageType())
                    .content(doc == null ? "" : doc.getContent())
                    .createdAt(i.getCreatedAt())
                    .agentRunId(isLatestRunMessage ? String.valueOf(agentRun.getId()) : null)
                    .agentStatus(isLatestRunMessage ? agentRun.getStatus() : null)
                    .agentSteps(isLatestRunMessage ? stepsByRunId.getOrDefault(agentRun.getId(), List.of()) : List.of())
                    .emailProposal(isLatestRunMessage ? emailProposalsByRunId.get(agentRun.getId()) : null)
                    .taskListProposal(isLatestRunMessage ? taskListProposalsByRunId.get(agentRun.getId()) : null)
                    .collaborationDocumentProposal(isLatestRunMessage ? collaborationDocumentProposalsByRunId.get(agentRun.getId()) : null)
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

    private Map<String, AiAgentRun> loadAgentRunsByTraceId(List<AiChatMessageIndex> indexes) {
        List<String> traceIds = indexes.stream()
            .filter(index -> SENDER_ASSISTANT.equals(index.getSenderType()))
            .map(AiChatMessageIndex::getTraceId)
            .filter(traceId -> traceId != null && !traceId.isBlank())
            .distinct()
            .toList();
        if (traceIds.isEmpty()) {
            return Map.of();
        }
        return agentRunMapper.selectList(new LambdaQueryWrapper<AiAgentRun>()
                .in(AiAgentRun::getTraceId, traceIds))
            .stream()
            .collect(Collectors.toMap(AiAgentRun::getTraceId, run -> run, (newer, ignored) -> newer));
    }

    private AiChatMessageIndex findLatestAssistantMessage(Long sessionId, String traceId) {
        if (sessionId == null || traceId == null || traceId.isBlank()) {
            return null;
        }
        return aiChatMessageIndexService.list(new LambdaQueryWrapper<AiChatMessageIndex>()
                .eq(AiChatMessageIndex::getSessionId, sessionId)
                .eq(AiChatMessageIndex::getTraceId, traceId)
                .eq(AiChatMessageIndex::getSenderType, SENDER_ASSISTANT)
                .orderByDesc(AiChatMessageIndex::getCreatedAt)
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
    }

    private String loadMongoMessageContent(String mongoMessageId) {
        if (mongoMessageId == null || mongoMessageId.isBlank()) {
            return "";
        }
        return mentorChatMessageRepository.findById(mongoMessageId)
            .map(MentorChatMessageDoc::getContent)
            .orElse("");
    }

    private Map<Long, List<MentorAgentStepVO>> loadAgentStepsByRunId(java.util.Collection<AiAgentRun> runs) {
        List<Long> runIds = runs.stream()
            .map(AiAgentRun::getId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        if (runIds.isEmpty()) {
            return Map.of();
        }
        return agentStepMapper.selectList(new LambdaQueryWrapper<AiAgentStep>()
                .in(AiAgentStep::getRunId, runIds)
                .orderByAsc(AiAgentStep::getRunId)
                .orderByAsc(AiAgentStep::getStepNo))
            .stream()
            .collect(Collectors.groupingBy(AiAgentStep::getRunId, LinkedHashMap::new, Collectors.mapping(step ->
                MentorAgentStepVO.builder()
                    .stepType(step.getStepType())
                    .toolName(step.getToolName())
                    .status(step.getStatus())
                    .summary(step.getDecisionSummary())
                    .build(), Collectors.toList())));
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

    private void sendAgentStatus(SseEmitter emitter, AgentRunService.AgentRunProgress progress) {
        try {
            emitter.send(SseEmitter.event()
                .name(EVENT_AGENT_STATUS)
                .data(objectMapper.writeValueAsString(progress)));
        } catch (IOException e) {
            log.debug("Send agent status failed, runId={}", progress.runId(), e);
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

    private AgentProfile resolveAgentProfile(AiChatSession session) {
        if (session != null && SESSION_TYPE_COLLAB_DOC.equals(resolveSessionType(session.getSessionType()))) {
            return COLLAB_DOCUMENT_PROFILE;
        }
        return TEAM_MENTOR_PROFILE;
    }

    private Map<String, Object> buildToolContext(Long userId, Long requestTeamId, AiChatSession session) {
        Map<String, Object> context = new HashMap<>(6);
        if (userId != null) {
            context.put(TOOL_CTX_USER_ID, userId);
        }
        Long resolvedTeamId = requestTeamId != null ? requestTeamId : session.getTeamId();
        if (resolvedTeamId != null) {
            context.put(TOOL_CTX_TEAM_ID, resolvedTeamId);
        }
        context.put(TOOL_CTX_SESSION_TYPE, resolveSessionType(session.getSessionType()));
        if (session.getDocumentId() != null) {
            context.put(TOOL_CTX_DOCUMENT_ID, session.getDocumentId());
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
        String historyPrompt = buildSlidingWindowHistoryPromptFromStore(sessionId, null);
        if (historyPrompt == null || historyPrompt.isBlank()) {
            teamRedisCacheService.evictChatHistory(sessionId);
            return;
        }
        teamRedisCacheService.putRaw(teamRedisCacheService.chatHistoryKey(sessionId), historyPrompt,
            TeamRedisCacheService.CHAT_HISTORY_TTL);
    }

    private String buildSystemPrompt(AgentProfile profile, String historyPrompt) {
        if (historyPrompt == null || historyPrompt.isBlank()) {
            return profile.systemPrompt();
        }
        return profile.systemPrompt() + "\n\n" + historyPrompt;
    }

    private String buildSlidingWindowHistoryPrompt(Long sessionId, Long excludedMessageId) {
        if (excludedMessageId != null) {
            String cached = teamRedisCacheService.getRaw(teamRedisCacheService.chatHistoryKey(sessionId));
            if (cached != null) {
                return cached;
            }
        }
        return buildSlidingWindowHistoryPromptFromStore(sessionId, excludedMessageId);
    }

    private String buildSlidingWindowHistoryPromptFromStore(Long sessionId, Long excludedMessageId) {
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
        // 不信任历史 Redis/MySQL 缓存：它们曾保存包含 @ 文档全文的 Prompt token。
        // 唯一口径是关联 Mongo chat_messages 文档的 content 字段。
        int contentTokenCount = estimateTokenCount(doc == null ? "" : doc.getContent());
        if (!Objects.equals(index.getTokenCount(), contentTokenCount)) {
            AiChatMessageIndex update = new AiChatMessageIndex();
            update.setId(index.getId());
            update.setTokenCount(contentTokenCount);
            aiChatMessageIndexService.updateById(update);
            // 令短期记忆在下一次访问时用已纠正的索引重新构建，避免延续旧统计。
            if (index.getSessionId() != null) {
                teamRedisCacheService.evictShortTermChatMemory(index.getSessionId());
            }
        }
        index.setTokenCount(contentTokenCount);
        return contentTokenCount;
    }

    /**
     * ai_chat_message_index.token_count 的唯一统计口径：Mongo chat_messages.content 的文本 token。
     */
    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, tokenCountEstimator.estimate(content));
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
    private static class UsageUsageHolder {
        private Integer completionTokens;
        private Integer promptTokens;
    }

    private record AgentProfile(String systemPrompt, List<String> toolNames) {
    }
}
