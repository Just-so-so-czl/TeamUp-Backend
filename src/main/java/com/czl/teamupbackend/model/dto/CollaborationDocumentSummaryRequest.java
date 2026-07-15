package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "协作文档摘要请求")
public class CollaborationDocumentSummaryRequest {

    @Schema(description = "协作文档ID")
    private Long documentId;
}
