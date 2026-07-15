package com.czl.teamupbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.event.CollaborationDocumentSummaryRequestedEvent;
import com.czl.teamupbackend.mapper.DocumentMapper;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.model.entity.Document;
import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.model.vo.CollaborationDocumentSummaryVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationDocumentSummaryService {

    private static final int TYPE_COLLAB = 2;
    private static final String COLLECTION = "collaboration_documents";
    private static final String DELAY_QUEUE_KEY = "collaboration:summary:delay-queue";
    private static final String LOCK_KEY_PREFIX = "collaboration:summary:lock:";
    private static final long QUIET_WINDOW_MILLIS = 10 * 60 * 1000L;
    private static final long SUMMARY_LOCK_MILLIS = 30 * 60 * 1000L;
    private static final int MAX_DUE_DOCUMENTS_PER_SCAN = 100;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private static final DefaultRedisScript<Long> POP_DUE_DOCUMENT_SCRIPT = new DefaultRedisScript<>(
        """
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if score and tonumber(score) <= tonumber(ARGV[2]) then
                return redis.call('ZREM', KEYS[1], ARGV[1])
            end
            return 0
            """,
        Long.class
    );

    private final DocumentMapper documentMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 协同服务每次完成内容快照持久化后调用。ZSet 的 score 即新的到期时间，
     * 对同一 documentId 的重复写入会覆盖旧 score，从而实现防抖重置。
     */
    public void recordDocumentChanged(String documentId) {
        if (!StringUtils.hasText(documentId)) {
            return;
        }
        long dueAt = System.currentTimeMillis() + QUIET_WINDOW_MILLIS;
        stringRedisTemplate.opsForZSet().add(DELAY_QUEUE_KEY, documentId.trim(), dueAt);
        log.debug("Collaboration summary debounce reset, documentId={}, dueAt={}", documentId, dueAt);
    }

    @Scheduled(fixedDelayString = "${collaboration-summary.delay-queue.scan-interval-ms:10000}")
    public void processDueSummaryRequests() {
        long now = System.currentTimeMillis();
        Set<String> documentIds = stringRedisTemplate.opsForZSet().rangeByScore(
            DELAY_QUEUE_KEY,
            0,
            now,
            0,
            MAX_DUE_DOCUMENTS_PER_SCAN
        );
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        for (String documentId : documentIds) {
            Long popped = stringRedisTemplate.execute(
                POP_DUE_DOCUMENT_SCRIPT,
                List.of(DELAY_QUEUE_KEY),
                documentId,
                String.valueOf(now)
            );
            if (popped == null || popped == 0L) {
                continue;
            }
            try {
                requestAutomaticSummary(Long.parseLong(documentId));
            } catch (NumberFormatException e) {
                log.warn("Ignoring invalid collaboration summary queue item, documentId={}", documentId);
            } catch (Exception e) {
                log.error("Failed to process collaboration summary queue item, documentId={}", documentId, e);
            }
        }
    }

    public CollaborationDocumentSummaryVO requestManualSummary(Long currentUserId, Long documentId) {
        Document document = getCollaborationDocument(documentId);
        validateMembership(currentUserId, document.getTeamId());
        Snapshot snapshot = loadSnapshot(documentId);
        if (!StringUtils.hasText(snapshot.plainText())) {
            throw new BizException(400, "协作文档暂无可摘要的文本内容");
        }
        if (!snapshot.contentChanged()) {
            return buildStatus(documentId, snapshot, "文档内容未变化，无需更新摘要");
        }
        if (STATUS_PROCESSING.equals(snapshot.summaryStatus()) || !tryLock(documentId)) {
            return buildStatus(documentId, snapshot.withSummaryStatus(STATUS_PROCESSING), "摘要正在生成中，请稍后查看");
        }
        markProcessing(documentId);
        publishSummaryRequest(document, snapshot, "MANUAL");
        return buildStatus(documentId, snapshot.withSummaryStatus(STATUS_PROCESSING), "已提交摘要生成任务");
    }

    public CollaborationDocumentSummaryVO getSummaryStatus(Long currentUserId, Long documentId) {
        Document document = getCollaborationDocument(documentId);
        validateMembership(currentUserId, document.getTeamId());
        return buildStatus(documentId, loadSnapshot(documentId), null);
    }

    public boolean completeSummary(CollaborationDocumentSummaryRequestedEvent event, String summary) {
        Query query = sourceUnchangedQuery(event);
        Update update = new Update()
            .set("ai_summary", summary)
            .set("summary_status", STATUS_SUCCESS)
            .set("summary_error", null)
            .set("summary_generated_at", LocalDateTime.now())
            .set("summary_source_text_hash", event.sourceTextHash())
            .set("summary_source_text_length", event.sourceTextLength());
        long modifiedCount = mongoTemplate.updateFirst(query, update, COLLECTION).getModifiedCount();
        if (modifiedCount == 0) {
            resetToPendingAndDebounce(event.documentId());
            log.info("Collaboration summary result discarded because content changed, documentId={}", event.documentId());
            return false;
        }
        log.info("Collaboration summary generated, documentId={}, triggerType={}, summaryLength={}",
            event.documentId(), event.triggerType(), summary.length());
        return true;
    }

    public void failSummary(CollaborationDocumentSummaryRequestedEvent event, Exception exception) {
        Query query = sourceUnchangedQuery(event);
        Update update = new Update()
            .set("summary_status", STATUS_FAILED)
            .set("summary_error", truncateError(exception));
        long modifiedCount = mongoTemplate.updateFirst(query, update, COLLECTION).getModifiedCount();
        if (modifiedCount == 0) {
            resetToPendingAndDebounce(event.documentId());
        }
    }

    public void releaseSummaryLock(Long documentId) {
        if (documentId != null) {
            stringRedisTemplate.delete(LOCK_KEY_PREFIX + documentId);
        }
    }

    private void requestAutomaticSummary(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null || !Integer.valueOf(TYPE_COLLAB).equals(document.getType())) {
            log.info("Collaboration summary skipped because document is unavailable, documentId={}", documentId);
            return;
        }
        Snapshot snapshot = loadSnapshot(documentId);
        if (!StringUtils.hasText(snapshot.plainText())) {
            log.info("Collaboration summary skipped because document content is empty, documentId={}", documentId);
            return;
        }
        if (!shouldGenerateAutomatically(snapshot)) {
            log.info("Collaboration summary skipped because text change is below threshold, documentId={}", documentId);
            return;
        }
        if (STATUS_PROCESSING.equals(snapshot.summaryStatus()) || !tryLock(documentId)) {
            return;
        }
        markProcessing(documentId);
        publishSummaryRequest(document, snapshot, "AUTO");
    }

    private void publishSummaryRequest(Document document, Snapshot snapshot, String triggerType) {
        applicationEventPublisher.publishEvent(new CollaborationDocumentSummaryRequestedEvent(
            document.getId(),
            document.getTeamId(),
            document.getTitle(),
            snapshot.plainText(),
            snapshot.contentHash(),
            snapshot.textLength(),
            triggerType
        ));
    }

    private boolean shouldGenerateAutomatically(Snapshot snapshot) {
        if (!snapshot.hasSummary()) {
            return true;
        }
        if (!snapshot.contentChanged()) {
            return false;
        }
        int previousLength = Math.max(snapshot.summarySourceTextLength(), 1);
        return Math.abs(snapshot.textLength() - snapshot.summarySourceTextLength()) > previousLength * 0.2D;
    }

    private boolean tryLock(Long documentId) {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
            LOCK_KEY_PREFIX + documentId,
            "1",
            java.time.Duration.ofMillis(SUMMARY_LOCK_MILLIS)
        );
        return Boolean.TRUE.equals(locked);
    }

    private void markProcessing(Long documentId) {
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("docId").is(String.valueOf(documentId))),
            new Update()
                .set("summary_status", STATUS_PROCESSING)
                .set("summary_error", null)
                .set("summary_processing_started_at", LocalDateTime.now()),
            COLLECTION
        );
    }

    private void resetToPendingAndDebounce(Long documentId) {
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("docId").is(String.valueOf(documentId))
                .and("summary_status").is(STATUS_PROCESSING)),
            new Update().set("summary_status", STATUS_PENDING).set("summary_error", null),
            COLLECTION
        );
        recordDocumentChanged(String.valueOf(documentId));
    }

    private Query sourceUnchangedQuery(CollaborationDocumentSummaryRequestedEvent event) {
        return Query.query(Criteria.where("docId").is(String.valueOf(event.documentId()))
            .and("plain_text").is(event.sourceText()));
    }

    private Snapshot loadSnapshot(Long documentId) {
        org.bson.Document mongoDocument = mongoTemplate.findOne(
            Query.query(Criteria.where("docId").is(String.valueOf(documentId))),
            org.bson.Document.class,
            COLLECTION
        );
        if (mongoDocument == null) {
            return Snapshot.empty();
        }
        String plainText = mongoDocument.getString("plain_text");
        String summary = mongoDocument.getString("ai_summary");
        String sourceHash = mongoDocument.getString("summary_source_text_hash");
        int textLength = countCharacters(plainText);
        return new Snapshot(
            plainText == null ? "" : plainText,
            textLength,
            hash(plainText == null ? "" : plainText),
            summary,
            defaultStatus(mongoDocument.getString("summary_status")),
            mongoDocument.getString("summary_error"),
            toLocalDateTime(mongoDocument.get("summary_generated_at")),
            numberValue(mongoDocument.get("summary_source_text_length")),
            !StringUtils.hasText(summary) || !StringUtils.hasText(sourceHash) || !sourceHash.equals(hash(plainText == null ? "" : plainText))
        );
    }

    private CollaborationDocumentSummaryVO buildStatus(Long documentId, Snapshot snapshot, String message) {
        return CollaborationDocumentSummaryVO.builder()
            .documentId(documentId)
            .summary(snapshot.summary())
            .summaryStatus(snapshot.summaryStatus())
            .summaryError(snapshot.summaryError())
            .summaryGeneratedAt(snapshot.summaryGeneratedAt())
            .contentChanged(snapshot.contentChanged())
            .message(message)
            .build();
    }

    private Document getCollaborationDocument(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new BizException(400, "文档ID不合法");
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(404, "文档不存在");
        }
        if (!Integer.valueOf(TYPE_COLLAB).equals(document.getType())) {
            throw new BizException(400, "仅支持协作文档摘要");
        }
        return document;
    }

    private void validateMembership(Long currentUserId, Long teamId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BizException(401, "未登录");
        }
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, teamId)
            .eq(TeamMember::getUserId, currentUserId)
            .last("limit 1"));
        if (member == null) {
            throw new BizException(403, "你不是该小组成员");
        }
    }

    private int countCharacters(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.toIntExact(text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).count());
    }

    private String hash(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        return null;
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status : STATUS_PENDING;
    }

    private String truncateError(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record Snapshot(
        String plainText,
        int textLength,
        String contentHash,
        String summary,
        String summaryStatus,
        String summaryError,
        LocalDateTime summaryGeneratedAt,
        int summarySourceTextLength,
        boolean contentChanged
    ) {
        static Snapshot empty() {
            return new Snapshot("", 0, "", null, STATUS_PENDING, null, null, 0, true);
        }

        boolean hasSummary() {
            return StringUtils.hasText(summary);
        }

        Snapshot withSummaryStatus(String status) {
            return new Snapshot(plainText, textLength, contentHash, summary, status, summaryError,
                summaryGeneratedAt, summarySourceTextLength, contentChanged);
        }
    }
}
