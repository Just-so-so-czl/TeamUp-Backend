package com.czl.teamupbackend.mq;

import com.czl.teamupbackend.config.DocumentTextExtractRabbitConfig;
import com.czl.teamupbackend.mapper.DocumentMapper;
import com.czl.teamupbackend.model.entity.Document;
import com.czl.teamupbackend.model.mongo.DocumentContentDoc;
import com.czl.teamupbackend.model.mq.DocumentTextExtractMessage;
import com.czl.teamupbackend.repository.DocumentContentRepository;
import com.czl.teamupbackend.service.IOssService;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 使用 Apache Tika 将资料文档转为纯文本，并保存到 MongoDB。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTextExtractConsumer {

    private static final int TYPE_RESOURCE = 1;
    private static final int MAX_EXTRACTED_TEXT_LENGTH = 2_000_000;
    private static final int SUMMARY_CHUNK_LENGTH = 6_000;
    private static final int MAX_SUMMARY_CHUNK_COUNT = 12;
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DOCUMENT_SUMMARY_PROMPT = """
        你是一名大学生小组学习项目的文档助手。请根据给出的资料文档内容生成准确、简明、可供团队导师使用的摘要。

        请严格基于原文，不要补充原文未提及的事实。优先保留项目目标、作业或实验要求、交付物、截止时间、评分标准、关键知识点、任务建议和风险提示；没有的信息请不要编造。

        使用中文输出，并按以下结构组织：
        1. 文档概述
        2. 关键要求或知识点
        3. 交付物、时间与评分信息
        4. 建议关注的风险或待确认事项

        文档标题：%s
        文档内容：
        %s
        """;
    private static final String MERGE_SUMMARY_PROMPT = """
        请将同一份资料文档的分段摘要整合为一份供团队导师使用的最终摘要。
        严格依据分段摘要，不要捏造信息；去除重复内容，并优先保留项目目标、任务要求、交付物、截止时间、评分标准、关键知识点和风险提示。
        使用中文输出，并按“文档概述、关键要求或知识点、交付物/时间/评分信息、风险或待确认事项”组织。

        文档标题：%s
        分段摘要：
        %s
        """;

    private final DocumentMapper documentMapper;
    private final DocumentContentRepository documentContentRepository;
    private final IOssService ossService;
    private final ChatClient.Builder chatClientBuilder;
    private final Tika tika = new Tika();

    @Value("${spring.ai.openai.summary.model}")
    private String summaryModel;

    @RabbitListener(queues = DocumentTextExtractRabbitConfig.QUEUE)
    public void consume(DocumentTextExtractMessage message) {
        if (message == null || message.getDocumentId() == null) {
            log.warn("Ignoring invalid document text extraction message");
            return;
        }

        Document document = documentMapper.selectById(message.getDocumentId());
        if (document == null) {
            log.warn("Document text extraction skipped because document does not exist, documentId={}", message.getDocumentId());
            return;
        }
        if (!Integer.valueOf(TYPE_RESOURCE).equals(document.getType())) {
            log.info("Document text extraction skipped because document is not a resource document, documentId={}, type={}",
                document.getId(), document.getType());
            return;
        }

        DocumentContentDoc contentDoc = getOrCreateContentDoc(document);
        if (STATUS_SUCCESS.equals(contentDoc.getParseStatus())) {
            generateSummaryIfNeeded(document, contentDoc);
            return;
        }

        markProcessing(contentDoc);
        try (InputStream inputStream = ossService.download(document.getStoragePath())) {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, buildFileName(document));
            String extractedText = tika.parseToString(inputStream, metadata, MAX_EXTRACTED_TEXT_LENGTH);
            saveSuccess(contentDoc, extractedText == null ? "" : extractedText);
            generateSummaryIfNeeded(document, contentDoc);
            log.info("Document text extracted successfully, documentId={}, textLength={}",
                document.getId(), contentDoc.getExtractedTextLength());
        } catch (Exception e) {
            saveFailure(contentDoc, e);
            log.error("Document text extraction failed, documentId={}", document.getId(), e);
        }
    }

    private DocumentContentDoc getOrCreateContentDoc(Document document) {
        return documentContentRepository.findByDocumentId(document.getId()).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            DocumentContentDoc contentDoc = new DocumentContentDoc();
            contentDoc.setDocumentId(document.getId());
            contentDoc.setTeamId(document.getTeamId());
            contentDoc.setFileType(document.getFileType());
            contentDoc.setParseStatus("PENDING");
            contentDoc.setAiSummary(null);
            contentDoc.setSummaryStatus("PENDING");
            contentDoc.setCreatedAt(now);
            contentDoc.setUpdatedAt(now);
            return documentContentRepository.save(contentDoc);
        });
    }

    private void markProcessing(DocumentContentDoc contentDoc) {
        contentDoc.setParseStatus(STATUS_PROCESSING);
        contentDoc.setParseError(null);
        contentDoc.setUpdatedAt(LocalDateTime.now());
        documentContentRepository.save(contentDoc);
    }

    private void saveSuccess(DocumentContentDoc contentDoc, String extractedText) {
        LocalDateTime now = LocalDateTime.now();
        contentDoc.setParseStatus(STATUS_SUCCESS);
        contentDoc.setParseError(null);
        contentDoc.setExtractedText(extractedText);
        contentDoc.setExtractedTextLength(extractedText.length());
        contentDoc.setTextTruncated(extractedText.length() >= MAX_EXTRACTED_TEXT_LENGTH);
        contentDoc.setExtractedAt(now);
        contentDoc.setUpdatedAt(now);
        documentContentRepository.save(contentDoc);
    }

    private void generateSummaryIfNeeded(Document document, DocumentContentDoc contentDoc) {
        if (STATUS_SUCCESS.equals(contentDoc.getSummaryStatus()) && hasText(contentDoc.getAiSummary())) {
            return;
        }
        if (!hasText(contentDoc.getExtractedText())) {
            contentDoc.setSummaryStatus(STATUS_FAILED);
            contentDoc.setSummaryError("提取文本为空，无法生成摘要");
            contentDoc.setUpdatedAt(LocalDateTime.now());
            documentContentRepository.save(contentDoc);
            return;
        }

        contentDoc.setSummaryStatus(STATUS_PROCESSING);
        contentDoc.setSummaryError(null);
        contentDoc.setUpdatedAt(LocalDateTime.now());
        documentContentRepository.save(contentDoc);

        try {
            String summary = generateSummary(document.getTitle(), contentDoc.getExtractedText());
            if (!hasText(summary)) {
                throw new IllegalStateException("摘要模型返回为空");
            }
            LocalDateTime now = LocalDateTime.now();
            contentDoc.setAiSummary(summary);
            contentDoc.setSummaryStatus(STATUS_SUCCESS);
            contentDoc.setSummaryError(null);
            contentDoc.setSummaryGeneratedAt(now);
            contentDoc.setUpdatedAt(now);
            documentContentRepository.save(contentDoc);
            log.info("Document AI summary generated successfully, documentId={}, summaryLength={}",
                document.getId(), summary.length());
        } catch (Exception e) {
            contentDoc.setSummaryStatus(STATUS_FAILED);
            contentDoc.setSummaryError(truncateError(e));
            contentDoc.setUpdatedAt(LocalDateTime.now());
            documentContentRepository.save(contentDoc);
            log.error("Document AI summary generation failed, documentId={}", document.getId(), e);
        }
    }

    /**
     * 复用资料文档的分段摘要能力，为协作文档快照生成摘要。
     *
     * @param title 文档标题
     * @param text 待摘要的纯文本快照
     * @return AI 生成的摘要
     */
    public String generateDocumentSummary(String title, String text) {
        if (!hasText(text)) {
            throw new IllegalArgumentException("文档内容为空，无法生成摘要");
        }
        return generateSummary(title, text);
    }

    private String generateSummary(String title, String extractedText) {
        List<String> chunks = splitSummaryChunks(extractedText);
        List<String> partialSummaries = new ArrayList<>(chunks.size());
        for (String chunk : chunks) {
            partialSummaries.add(callSummaryModel(String.format(DOCUMENT_SUMMARY_PROMPT, safeTitle(title), chunk)));
        }
        if (partialSummaries.size() == 1) {
            return partialSummaries.get(0);
        }
        return callSummaryModel(String.format(
            MERGE_SUMMARY_PROMPT,
            safeTitle(title),
            String.join("\n\n--- 分段摘要 ---\n\n", partialSummaries)
        ));
    }

    private List<String> splitSummaryChunks(String text) {
        String normalizedText = text.trim();
        int totalChunkCount = (normalizedText.length() + SUMMARY_CHUNK_LENGTH - 1) / SUMMARY_CHUNK_LENGTH;
        int selectedChunkCount = Math.min(totalChunkCount, MAX_SUMMARY_CHUNK_COUNT);
        List<String> chunks = new ArrayList<>(selectedChunkCount);
        for (int i = 0; i < selectedChunkCount; i++) {
            int start = totalChunkCount <= MAX_SUMMARY_CHUNK_COUNT
                ? i * SUMMARY_CHUNK_LENGTH
                : (int) ((long) i * normalizedText.length() / selectedChunkCount);
            int end = totalChunkCount <= MAX_SUMMARY_CHUNK_COUNT
                ? Math.min(start + SUMMARY_CHUNK_LENGTH, normalizedText.length())
                : Math.min(start + SUMMARY_CHUNK_LENGTH, normalizedText.length());
            chunks.add(normalizedText.substring(start, end));
        }
        return chunks;
    }

    private String callSummaryModel(String prompt) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(summaryModel)
            .temperature(0.2)
            .build();
        String content = chatClientBuilder.build()
            .prompt()
            .user(prompt)
            .options(options)
            .call()
            .content();
        return content == null ? "" : content.trim();
    }

    private void saveFailure(DocumentContentDoc contentDoc, Exception exception) {
        contentDoc.setParseStatus(STATUS_FAILED);
        contentDoc.setParseError(truncateError(exception));
        contentDoc.setUpdatedAt(LocalDateTime.now());
        documentContentRepository.save(contentDoc);
    }

    private String buildFileName(Document document) {
        String title = document.getTitle() == null ? "document" : document.getTitle();
        String fileType = document.getFileType();
        return fileType == null || fileType.isBlank() || title.toLowerCase().endsWith("." + fileType.toLowerCase())
            ? title
            : title + "." + fileType;
    }

    private String truncateError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeTitle(String title) {
        return hasText(title) ? title.trim() : "未命名文档";
    }
}
