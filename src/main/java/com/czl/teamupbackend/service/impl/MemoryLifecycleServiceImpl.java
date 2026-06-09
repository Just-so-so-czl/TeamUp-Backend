package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.czl.teamupbackend.event.MemoryCompressionEvent;
import com.czl.teamupbackend.event.MemoryCompressionEventType;
import com.czl.teamupbackend.model.entity.AiChatMemoryState;
import com.czl.teamupbackend.model.entity.AiChatMessageIndex;
import com.czl.teamupbackend.model.entity.AiChatSession;
import com.czl.teamupbackend.model.mongo.MentorChatMessageDoc;
import com.czl.teamupbackend.model.mongo.MentorMemoryDoc;
import com.czl.teamupbackend.repository.MentorChatMessageRepository;
import com.czl.teamupbackend.repository.MentorMemoryDocRepository;
import com.czl.teamupbackend.service.IAiChatMemoryStateService;
import com.czl.teamupbackend.service.IAiChatMessageIndexService;
import com.czl.teamupbackend.service.IAiChatSessionService;
import com.czl.teamupbackend.service.MemoryLifecycleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryLifecycleServiceImpl implements MemoryLifecycleService {

    private static final int SHORT_TERM_HIGH_WATERMARK = 5000;
    private static final int SHORT_TERM_LOW_WATERMARK = 3000;
    private static final int MID_TERM_HIGH_WATERMARK = 4000;
    private static final int MID_TERM_LOW_WATERMARK = 1000;
    private static final int EARLY_TERM_MAX_TOKENS = 1500;
    private static final int MAX_RETRY_TIMES = 3;
    private static final String MEMORY_TYPE_MID = "MID";
    private static final String MEMORY_TYPE_EARLY = "EARLY";
    private static final String SENDER_USER = "USER";
    private static final String SENDER_ASSISTANT = "ASSISTANT";
    private static final int STATUS_DONE = 2;
    private static final String COMPRESS_STATUS_SUCCESS = "SUCCESS";
    private static final String COMPRESS_STATUS_FAILED = "FAILED";
    private static final Duration SHORT_TERM_STATE_TTL = Duration.ofDays(3);
    private static final Duration MEMORY_LOCK_TTL = Duration.ofMinutes(5);
    private static final String SHORT_TERM_STATE_KEY_PREFIX = "chat:memory:short-term:";
    private static final String MID_TERM_LOCK_KEY_PREFIX = "chat:memory:lock:mid:";
    private static final String EARLY_TERM_LOCK_KEY_PREFIX = "chat:memory:lock:early:";

    private static final PromptTemplate MID_TERM_SUMMARY_PROMPT = PromptTemplate.builder()
        .template("你是一个记忆压缩专家。请将以下对话记录提炼成密集的 Markdown 技术事实要点，保留核心战术细节（如：用户在处理什么任务,使用了什么方法），剔除无意义的口水话。原始记录：{chat_history}")
        .build();

    private static final PromptTemplate EARLY_TERM_SUMMARY_PROMPT = PromptTemplate.builder()
        .template("你是一个项目战略分析师。请将以下一周内的对话流水账做压缩总结。擦除中间调试过程，只留铁打的既定事实和架构结论。流水账记录：{mid_term_history}")
        .build();

    private final IAiChatMessageIndexService aiChatMessageIndexService;
    private final IAiChatMemoryStateService aiChatMemoryStateService;
    private final IAiChatSessionService aiChatSessionService;
    private final MentorChatMessageRepository mentorChatMessageRepository;
    private final MentorMemoryDocRepository mentorMemoryDocRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TokenCountEstimator tokenCountEstimator;
    private final ChatClient.Builder chatClientBuilder;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Qualifier("memoryLifecycleExecutor")
    private final Executor memoryLifecycleExecutor;

    @Value("${spring.ai.openai.summary.model}")
    private String summaryModel;

    @Override
    public void onShortTermMessageAdded(AiChatMessageIndex messageIndex) {
        if (messageIndex == null || messageIndex.getSessionId() == null || messageIndex.getTokenCount() == null
            || messageIndex.getTokenCount() <= 0 || messageIndex.getStatus() == null || messageIndex.getStatus() != STATUS_DONE) {
            return;
        }
        ShortTermMemoryState state = loadOrRebuildShortTermState(messageIndex.getSessionId(), messageIndex.getTeamId());
        if (state.addOrUpdateMessage(ShortTermMessageSnapshot.from(messageIndex))) {
            persistShortTermState(state);
        }
        if (SENDER_USER.equals(messageIndex.getSenderType())) {
            triggerMidTermCompression(messageIndex.getSessionId());
        }
    }

    @Override
    public void triggerMidTermCompression(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        applicationEventPublisher.publishEvent(new MemoryCompressionEvent(sessionId, MemoryCompressionEventType.MID_TERM_CHECK, 0));
    }

    @Override
    public void triggerEarlyTermCompression(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        applicationEventPublisher.publishEvent(new MemoryCompressionEvent(sessionId, MemoryCompressionEventType.EARLY_TERM_CHECK, 0));
    }

    @Override
    public void processMidTermCompression(MemoryCompressionEvent event) {
        if (event == null || event.getSessionId() == null) {
            return;
        }
        String lockKey = MID_TERM_LOCK_KEY_PREFIX + event.getSessionId();
        if (!acquireLock(lockKey)) {
            return;
        }
        try {
            ShortTermMemoryState state = loadOrRebuildShortTermState(event.getSessionId(), null);
            if (state.getTotalTokenCount() < SHORT_TERM_HIGH_WATERMARK) {
                return;
            }
            int shortTermTokenBefore = state.getTotalTokenCount() == null ? 0 : state.getTotalTokenCount();

            List<AiChatMessageIndex> activeMessages = listActiveShortTermMessages(event.getSessionId());
            CompressionSelection selection = selectMessagesForShortTermCompression(activeMessages);
            if (selection.getSelectedMessages().isEmpty()) {
                return;
            }

            String chatHistory = buildChatHistory(selection.getSelectedMessages());
            String summary = callSummaryModel(MID_TERM_SUMMARY_PROMPT, "chat_history", chatHistory);
            if (!StringUtils.hasText(summary)) {
                throw new IllegalStateException("mid-term summary result is blank");
            }

            int summaryTokenCount = estimateBySpringAi(summary);
            AiChatSession session = requireSession(event.getSessionId());
            MentorMemoryDoc midDoc = appendMemorySegment(session, MEMORY_TYPE_MID, summary, summaryTokenCount);

            markMessagesAsInactive(selection.getSelectedMessages());
            state.removeMessages(selection.getSelectedMessages().stream().map(AiChatMessageIndex::getId).collect(Collectors.toSet()));
            persistShortTermState(state);
            updateMemoryStateMidTerm(session, midDoc, state);
            markMidTermCompressionSuccess(
                session,
                shortTermTokenBefore,
                state.getTotalTokenCount(),
                summaryTokenCount,
                selection,
                midDoc
            );

            log.info(
                "Mid-term memory compression success, sessionId={}, shortTermTokenBefore={}, shortTermTokenAfter={}, removedMessageCount={}, removedTokenCount={}, summaryTokenCount={}, midTermTokenCount={}, removedMessages={}",
                event.getSessionId(),
                shortTermTokenBefore,
                state.getTotalTokenCount(),
                selection.getSelectedMessages().size(),
                selection.getRemovedTokenCount(),
                summaryTokenCount,
                midDoc.getTokenCount(),
                buildRemovedMessageLog(selection.getSelectedMessages())
            );

            if (midDoc.getTokenCount() != null && midDoc.getTokenCount() >= MID_TERM_HIGH_WATERMARK) {
                triggerEarlyTermCompression(event.getSessionId());
            }
        } catch (Exception ex) {
            markMidTermCompressionFailure(event.getSessionId(), ex);
            log.error("Mid-term memory compression failed, sessionId={}, retryCount={}",
                event.getSessionId(), event.getRetryCount(), ex);
            scheduleRetry(event, ex);
        } finally {
            releaseLock(lockKey);
        }
    }

    @Override
    public void processEarlyTermCompression(MemoryCompressionEvent event) {
        if (event == null || event.getSessionId() == null) {
            return;
        }
        String lockKey = EARLY_TERM_LOCK_KEY_PREFIX + event.getSessionId();
        if (!acquireLock(lockKey)) {
            return;
        }
        try {
            AiChatMemoryState memoryState = getOrCreateMemoryState(event.getSessionId(), null);
            if (memoryState.getMidTermTokenCount() == null || memoryState.getMidTermTokenCount() < MID_TERM_HIGH_WATERMARK) {
                return;
            }
            MentorMemoryDoc midDoc = loadMemoryDoc(event.getSessionId(), MEMORY_TYPE_MID).orElse(null);
            if (midDoc == null || midDoc.getSegments() == null || midDoc.getSegments().isEmpty()) {
                return;
            }
            int midTermTokenBefore = midDoc.getTokenCount() == null ? 0 : midDoc.getTokenCount();

            SegmentCompressionSelection selection = selectMidTermSegmentsForEarlyCompression(midDoc);
            if (selection.getRemovedSegments().isEmpty()) {
                return;
            }

            String midTermHistory = selection.getRemovedSegments().stream()
                .map(MentorMemoryDoc.MemorySegment::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
            String summary = callSummaryModel(EARLY_TERM_SUMMARY_PROMPT, "mid_term_history", midTermHistory);
            if (!StringUtils.hasText(summary)) {
                throw new IllegalStateException("early-term summary result is blank");
            }

            int summaryTokenCount = estimateBySpringAi(summary);
            AiChatSession session = requireSession(event.getSessionId());
            MentorMemoryDoc earlyDoc = appendMemorySegment(session, MEMORY_TYPE_EARLY, summary, summaryTokenCount);
            trimEarlyMemoryIfNeeded(earlyDoc);
            mentorMemoryDocRepository.save(earlyDoc);

            midDoc.setSegments(selection.getRemainingSegments());
            midDoc.setTokenCount(sumSegmentTokens(selection.getRemainingSegments()));
            midDoc.setUpdatedAt(LocalDateTime.now());
            mentorMemoryDocRepository.save(midDoc);

            memoryState.setMidTermMongoId(midDoc.getId());
            memoryState.setMidTermTokenCount(midDoc.getTokenCount());
            memoryState.setEarlyTermMongoId(earlyDoc.getId());
            memoryState.setEarlyTermTokenCount(earlyDoc.getTokenCount());
            aiChatMemoryStateService.saveOrUpdate(memoryState);
            markEarlyTermCompressionSuccess(memoryState, midTermTokenBefore, summaryTokenCount, selection, earlyDoc);

            log.info(
                "Early-term memory compression success, sessionId={}, midTermTokenBefore={}, midTermTokenAfter={}, removedSegmentCount={}, removedTokenCount={}, summaryTokenCount={}, earlyTermTokenCount={}, removedSegments={}",
                event.getSessionId(),
                midTermTokenBefore,
                midDoc.getTokenCount(),
                selection.getRemovedSegments().size(),
                sumSegmentTokens(selection.getRemovedSegments()),
                summaryTokenCount,
                earlyDoc.getTokenCount(),
                buildRemovedSegmentLog(selection.getRemovedSegments())
            );
        } catch (Exception ex) {
            markEarlyTermCompressionFailure(event.getSessionId(), ex);
            log.error("Early-term memory compression failed, sessionId={}, retryCount={}",
                event.getSessionId(), event.getRetryCount(), ex);
            scheduleRetry(event, ex);
        } finally {
            releaseLock(lockKey);
        }
    }

    private void scheduleRetry(MemoryCompressionEvent event, Exception ex) {
        int retryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();
        if (retryCount >= MAX_RETRY_TIMES) {
            log.warn("Memory compression retry exhausted, sessionId={}, eventType={}", event.getSessionId(), event.getEventType(), ex);
            return;
        }
        MemoryCompressionEvent retryEvent = new MemoryCompressionEvent(event.getSessionId(), event.getEventType(), retryCount + 1);
        CompletableFuture.runAsync(
            () -> applicationEventPublisher.publishEvent(retryEvent),
            CompletableFuture.delayedExecutor(Math.max(2, retryCount + 1), TimeUnit.SECONDS, memoryLifecycleExecutor)
        );
    }

    private String callSummaryModel(PromptTemplate promptTemplate, String variableName, String content) {
        Map<String, Object> vars = new HashMap<>(2);
        vars.put(variableName, content);
        String promptText = promptTemplate.render(vars);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(summaryModel)
            .temperature(0.2)
            .build();
        String summary = chatClientBuilder.build()
            .prompt()
            .user(promptText)
            .options(options)
            .call()
            .content();
        return summary == null ? "" : summary.trim();
    }

    private ShortTermMemoryState loadOrRebuildShortTermState(Long sessionId, Long fallbackTeamId) {
        String raw = stringRedisTemplate.opsForValue().get(shortTermStateKey(sessionId));
        if (StringUtils.hasText(raw)) {
            try {
                return objectMapper.readValue(raw, ShortTermMemoryState.class);
            } catch (Exception e) {
                log.warn("Parse short-term state failed, sessionId={}", sessionId, e);
            }
        }
        List<AiChatMessageIndex> activeMessages = listActiveShortTermMessages(sessionId);
        Long teamId = fallbackTeamId;
        if (teamId == null && !activeMessages.isEmpty()) {
            teamId = activeMessages.get(0).getTeamId();
        }
        if (teamId == null) {
            AiChatSession session = requireSession(sessionId);
            teamId = session.getTeamId();
        }
        ShortTermMemoryState state = new ShortTermMemoryState();
        state.setSessionId(sessionId);
        state.setTeamId(teamId);
        state.setMessages(activeMessages.stream().map(ShortTermMessageSnapshot::from).collect(Collectors.toList()));
        state.recalculate();
        persistShortTermState(state);
        return state;
    }

    private void persistShortTermState(ShortTermMemoryState state) {
        state.recalculate();
        try {
            String raw = objectMapper.writeValueAsString(state);
            stringRedisTemplate.opsForValue().set(shortTermStateKey(state.getSessionId()), raw, SHORT_TERM_STATE_TTL);
        } catch (Exception e) {
            log.warn("Persist short-term state failed, sessionId={}", state.getSessionId(), e);
        }
        AiChatMemoryState memoryState = getOrCreateMemoryState(state.getSessionId(), state.getTeamId());
        memoryState.setShortTermTokenCount(state.getTotalTokenCount());
        memoryState.setShortTermMessageCount(state.getMessages().size());
        aiChatMemoryStateService.saveOrUpdate(memoryState);
    }

    private AiChatMemoryState getOrCreateMemoryState(Long sessionId, Long fallbackTeamId) {
        AiChatMemoryState memoryState = aiChatMemoryStateService.getOne(new LambdaQueryWrapper<AiChatMemoryState>()
            .eq(AiChatMemoryState::getSessionId, sessionId)
            .last("limit 1"));
        if (memoryState != null) {
            return memoryState;
        }
        Long teamId = fallbackTeamId;
        if (teamId == null) {
            teamId = requireSession(sessionId).getTeamId();
        }
        return new AiChatMemoryState()
            .setSessionId(sessionId)
            .setTeamId(teamId)
            .setShortTermTokenCount(0)
            .setShortTermMessageCount(0)
            .setMidTermTokenCount(0)
            .setEarlyTermTokenCount(0);
    }

    private List<AiChatMessageIndex> listActiveShortTermMessages(Long sessionId) {
        return aiChatMessageIndexService.list(new LambdaQueryWrapper<AiChatMessageIndex>()
            .eq(AiChatMessageIndex::getSessionId, sessionId)
            .eq(AiChatMessageIndex::getDeleted, 0)
            .eq(AiChatMessageIndex::getStatus, STATUS_DONE)
            .and(wrapper -> wrapper.eq(AiChatMessageIndex::getShortTermActive, 1).or().isNull(AiChatMessageIndex::getShortTermActive))
            .orderByAsc(AiChatMessageIndex::getCreatedAt));
    }

    private CompressionSelection selectMessagesForShortTermCompression(List<AiChatMessageIndex> activeMessages) {
        int total = activeMessages.stream().map(AiChatMessageIndex::getTokenCount).filter(token -> token != null && token > 0).mapToInt(Integer::intValue).sum();
        int remain = total;
        List<AiChatMessageIndex> removed = new ArrayList<>();
        for (AiChatMessageIndex message : activeMessages) {
            Integer tokenCount = message.getTokenCount();
            if (tokenCount == null || tokenCount <= 0) {
                continue;
            }
            if (remain <= SHORT_TERM_LOW_WATERMARK) {
                break;
            }
            removed.add(message);
            remain -= tokenCount;
        }
        return new CompressionSelection(removed, total - remain, remain);
    }

    private SegmentCompressionSelection selectMidTermSegmentsForEarlyCompression(MentorMemoryDoc midDoc) {
        List<MentorMemoryDoc.MemorySegment> segments = midDoc.getSegments() == null ? new ArrayList<>() : new ArrayList<>(midDoc.getSegments());
        segments.sort(Comparator.comparing(MentorMemoryDoc.MemorySegment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        int remain = sumSegmentTokens(segments);
        List<MentorMemoryDoc.MemorySegment> removed = new ArrayList<>();
        List<MentorMemoryDoc.MemorySegment> remaining = new ArrayList<>(segments);
        while (!remaining.isEmpty() && remain > MID_TERM_LOW_WATERMARK) {
            MentorMemoryDoc.MemorySegment first = remaining.remove(0);
            removed.add(first);
            remain -= first.getTokenCount() == null ? 0 : first.getTokenCount();
        }
        return new SegmentCompressionSelection(removed, remaining);
    }

    private String buildChatHistory(List<AiChatMessageIndex> messages) {
        if (messages.isEmpty()) {
            return "";
        }
        Map<String, MentorChatMessageDoc> docMap = mentorChatMessageRepository.findByIdIn(messages.stream()
                .map(AiChatMessageIndex::getMongoMessageId)
                .filter(StringUtils::hasText)
                .toList())
            .stream()
            .collect(Collectors.toMap(MentorChatMessageDoc::getId, doc -> doc, (left, right) -> left, LinkedHashMap::new));
        StringBuilder builder = new StringBuilder();
        for (AiChatMessageIndex message : messages) {
            MentorChatMessageDoc doc = docMap.get(message.getMongoMessageId());
            if (doc == null || !StringUtils.hasText(doc.getContent())) {
                continue;
            }
            String role = SENDER_ASSISTANT.equals(message.getSenderType()) ? "导师" : "用户";
            builder.append(role).append("：").append(doc.getContent().trim()).append('\n');
        }
        return builder.toString().trim();
    }

    private MentorMemoryDoc appendMemorySegment(AiChatSession session, String memoryType, String content, int tokenCount) {
        MentorMemoryDoc doc = loadMemoryDoc(session.getId(), memoryType)
            .orElseGet(() -> {
                MentorMemoryDoc created = new MentorMemoryDoc();
                created.setSessionId(session.getId());
                created.setTeamId(session.getTeamId());
                created.setMemoryType(memoryType);
                created.setTokenCount(0);
                created.setCreatedAt(LocalDateTime.now());
                created.setUpdatedAt(LocalDateTime.now());
                return created;
            });
        if (doc.getSegments() == null) {
            doc.setSegments(new ArrayList<>());
        }
        doc.getSegments().add(new MentorMemoryDoc.MemorySegment(content, tokenCount, LocalDateTime.now()));
        doc.setTokenCount((doc.getTokenCount() == null ? 0 : doc.getTokenCount()) + tokenCount);
        doc.setUpdatedAt(LocalDateTime.now());
        return mentorMemoryDocRepository.save(doc);
    }

    private void trimEarlyMemoryIfNeeded(MentorMemoryDoc earlyDoc) {
        if (earlyDoc.getSegments() == null) {
            earlyDoc.setSegments(new ArrayList<>());
            earlyDoc.setTokenCount(0);
            return;
        }
        earlyDoc.getSegments().sort(Comparator.comparing(MentorMemoryDoc.MemorySegment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        int total = sumSegmentTokens(earlyDoc.getSegments());
        while (!earlyDoc.getSegments().isEmpty() && total > EARLY_TERM_MAX_TOKENS) {
            MentorMemoryDoc.MemorySegment removed = earlyDoc.getSegments().remove(0);
            total -= removed.getTokenCount() == null ? 0 : removed.getTokenCount();
        }
        earlyDoc.setTokenCount(Math.max(total, 0));
        earlyDoc.setUpdatedAt(LocalDateTime.now());
    }

    private void markMessagesAsInactive(List<AiChatMessageIndex> messages) {
        for (AiChatMessageIndex message : messages) {
            AiChatMessageIndex update = new AiChatMessageIndex();
            update.setId(message.getId());
            update.setShortTermActive(0);
            aiChatMessageIndexService.updateById(update);
        }
    }

    private Optional<MentorMemoryDoc> loadMemoryDoc(Long sessionId, String memoryType) {
        return mentorMemoryDocRepository.findBySessionIdAndMemoryType(sessionId, memoryType);
    }

    private void updateMemoryStateMidTerm(AiChatSession session, MentorMemoryDoc midDoc, ShortTermMemoryState state) {
        AiChatMemoryState memoryState = getOrCreateMemoryState(session.getId(), session.getTeamId());
        memoryState.setShortTermTokenCount(state.getTotalTokenCount());
        memoryState.setShortTermMessageCount(state.getMessages().size());
        memoryState.setMidTermMongoId(midDoc.getId());
        memoryState.setMidTermTokenCount(midDoc.getTokenCount());
        aiChatMemoryStateService.saveOrUpdate(memoryState);
    }

    private void markMidTermCompressionSuccess(
        AiChatSession session,
        Integer shortTermTokenBefore,
        Integer shortTermTokenAfter,
        Integer summaryTokenCount,
        CompressionSelection selection,
        MentorMemoryDoc midDoc
    ) {
        AiChatMemoryState memoryState = getOrCreateMemoryState(session.getId(), session.getTeamId());
        memoryState.setMidTermMongoId(midDoc.getId());
        memoryState.setMidTermTokenCount(midDoc.getTokenCount());
        memoryState.setLastMidTermCompressAt(LocalDateTime.now());
        memoryState.setLastMidTermSourceTokenCount(defaultInt(shortTermTokenBefore));
        memoryState.setLastMidTermTargetTokenCount(defaultInt(shortTermTokenAfter));
        memoryState.setLastMidTermSummaryTokenCount(defaultInt(summaryTokenCount));
        memoryState.setLastMidTermRemovedMessageCount(selection.getSelectedMessages().size());
        memoryState.setLastMidTermRemovedMessageIds(joinMessageIds(selection.getSelectedMessages()));
        memoryState.setLastMidTermStatus(COMPRESS_STATUS_SUCCESS);
        memoryState.setLastMidTermErrorMsg("");
        aiChatMemoryStateService.saveOrUpdate(memoryState);
    }

    private void markMidTermCompressionFailure(Long sessionId, Exception ex) {
        AiChatMemoryState memoryState = getOrCreateMemoryState(sessionId, null);
        memoryState.setLastMidTermCompressAt(LocalDateTime.now());
        memoryState.setLastMidTermStatus(COMPRESS_STATUS_FAILED);
        memoryState.setLastMidTermErrorMsg(truncateError(ex));
        aiChatMemoryStateService.saveOrUpdate(memoryState);
    }

    private void markEarlyTermCompressionSuccess(
        AiChatMemoryState memoryState,
        Integer midTermTokenBefore,
        Integer summaryTokenCount,
        SegmentCompressionSelection selection,
        MentorMemoryDoc earlyDoc
    ) {
        memoryState.setLastEarlyTermCompressAt(LocalDateTime.now());
        memoryState.setLastEarlyTermSourceTokenCount(defaultInt(midTermTokenBefore));
        memoryState.setLastEarlyTermTargetTokenCount(defaultInt(memoryState.getMidTermTokenCount()));
        memoryState.setLastEarlyTermSummaryTokenCount(defaultInt(summaryTokenCount));
        memoryState.setLastEarlyTermRemovedSegmentCount(selection.getRemovedSegments().size());
        memoryState.setLastEarlyTermRemovedTokenCount(sumSegmentTokens(selection.getRemovedSegments()));
        memoryState.setLastEarlyTermStatus(COMPRESS_STATUS_SUCCESS);
        memoryState.setLastEarlyTermErrorMsg("");
        memoryState.setEarlyTermMongoId(earlyDoc.getId());
        memoryState.setEarlyTermTokenCount(earlyDoc.getTokenCount());
        aiChatMemoryStateService.saveOrUpdate(memoryState);
    }

    private void markEarlyTermCompressionFailure(Long sessionId, Exception ex) {
        AiChatMemoryState memoryState = getOrCreateMemoryState(sessionId, null);
        memoryState.setLastEarlyTermCompressAt(LocalDateTime.now());
        memoryState.setLastEarlyTermStatus(COMPRESS_STATUS_FAILED);
        memoryState.setLastEarlyTermErrorMsg(truncateError(ex));
        aiChatMemoryStateService.saveOrUpdate(memoryState);
    }

    private int estimateBySpringAi(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, tokenCountEstimator.estimate(content));
    }

    private int sumSegmentTokens(List<MentorMemoryDoc.MemorySegment> segments) {
        return segments.stream()
            .map(MentorMemoryDoc.MemorySegment::getTokenCount)
            .filter(token -> token != null && token > 0)
            .mapToInt(Integer::intValue)
            .sum();
    }

    private AiChatSession requireSession(Long sessionId) {
        AiChatSession session = aiChatSessionService.getById(sessionId);
        if (session == null) {
            throw new IllegalStateException("session not found: " + sessionId);
        }
        return session;
    }

    private boolean acquireLock(String key) {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", MEMORY_LOCK_TTL);
        return Boolean.TRUE.equals(locked);
    }

    private void releaseLock(String key) {
        stringRedisTemplate.delete(key);
    }

    private String shortTermStateKey(Long sessionId) {
        return SHORT_TERM_STATE_KEY_PREFIX + sessionId;
    }

    private String joinMessageIds(List<AiChatMessageIndex> messages) {
        return messages.stream()
            .map(AiChatMessageIndex::getId)
            .filter(id -> id != null)
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }

    private String buildRemovedMessageLog(List<AiChatMessageIndex> messages) {
        return messages.stream()
            .map(message -> "{id=" + message.getId()
                + ",sender=" + message.getSenderType()
                + ",tokens=" + defaultInt(message.getTokenCount())
                + ",createdAt=" + message.getCreatedAt() + "}")
            .collect(Collectors.joining(", "));
    }

    private String buildRemovedSegmentLog(List<MentorMemoryDoc.MemorySegment> segments) {
        return segments.stream()
            .map(segment -> "{tokens=" + defaultInt(segment.getTokenCount())
                + ",createdAt=" + segment.getCreatedAt()
                + ",preview=" + abbreviate(segment.getContent(), 80) + "}")
            .collect(Collectors.joining(", "));
    }

    private String abbreviate(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncateError(Exception ex) {
        String message = ex == null ? "" : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            message = ex == null ? "" : ex.getClass().getSimpleName();
        }
        return abbreviate(message, 500);
    }

    @Data
    private static class ShortTermMemoryState {
        private Long sessionId;
        private Long teamId;
        private Integer totalTokenCount = 0;
        private List<ShortTermMessageSnapshot> messages = new ArrayList<>();

        boolean addOrUpdateMessage(ShortTermMessageSnapshot snapshot) {
            if (snapshot == null || snapshot.getMessageId() == null) {
                return false;
            }
            for (int i = 0; i < messages.size(); i++) {
                if (snapshot.getMessageId().equals(messages.get(i).getMessageId())) {
                    messages.set(i, snapshot);
                    recalculate();
                    return true;
                }
            }
            messages.add(snapshot);
            messages.sort(Comparator.comparing(ShortTermMessageSnapshot::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            recalculate();
            return true;
        }

        void removeMessages(Set<Long> messageIds) {
            if (messageIds == null || messageIds.isEmpty()) {
                return;
            }
            messages.removeIf(item -> messageIds.contains(item.getMessageId()));
            recalculate();
        }

        void recalculate() {
            this.totalTokenCount = messages.stream()
                .map(ShortTermMessageSnapshot::getTokenCount)
                .filter(token -> token != null && token > 0)
                .mapToInt(Integer::intValue)
                .sum();
        }
    }

    @Data
    @AllArgsConstructor
    private static class ShortTermMessageSnapshot {
        private Long messageId;
        private String senderType;
        private Integer tokenCount;
        private LocalDateTime createdAt;

        static ShortTermMessageSnapshot from(AiChatMessageIndex index) {
            return new ShortTermMessageSnapshot(index.getId(), index.getSenderType(), index.getTokenCount(), index.getCreatedAt());
        }
    }

    @Data
    @AllArgsConstructor
    private static class CompressionSelection {
        private List<AiChatMessageIndex> selectedMessages;
        private Integer removedTokenCount;
        private Integer remainingTokenCount;
    }

    @Data
    @AllArgsConstructor
    private static class SegmentCompressionSelection {
        private List<MentorMemoryDoc.MemorySegment> removedSegments;
        private List<MentorMemoryDoc.MemorySegment> remainingSegments;
    }
}
