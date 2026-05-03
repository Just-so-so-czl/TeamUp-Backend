package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文档删除请求")
public class DocumentDeleteRequest {

    @Schema(description = "文档ID")
    private Long documentId;
}

