package com.czl.teamupbackend.model.mongo;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 资料文档的可检索正文与 AI 产物。
 */
@Data
@Document(collection = "document_contents")
@CompoundIndexes({
    @CompoundIndex(name = "uk_document_id", def = "{'documentId': 1}", unique = true),
    @CompoundIndex(name = "idx_team_id_status", def = "{'teamId': 1, 'parseStatus': 1}")
})
public class DocumentContentDoc {

    @Id
    private String id;

    private Long documentId;

    private Long teamId;

    private String fileType;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String parseStatus;

    private String parseError;

    /** Apache Tika 提取的纯文本。 */
    private String extractedText;

    private Integer extractedTextLength;

    private Boolean textTruncated;

    /**
     * 大模型生成的文档摘要。
     */
    private String aiSummary;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String summaryStatus;

    private String summaryError;

    private LocalDateTime summaryGeneratedAt;

    private LocalDateTime extractedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
