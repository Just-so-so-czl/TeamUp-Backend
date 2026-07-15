package com.czl.teamupbackend.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CollaborationDocumentSummaryVO {

    private Long documentId;

    private String summary;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String summaryStatus;

    private String summaryError;

    private LocalDateTime summaryGeneratedAt;

    private Boolean contentChanged;

    private String message;
}
