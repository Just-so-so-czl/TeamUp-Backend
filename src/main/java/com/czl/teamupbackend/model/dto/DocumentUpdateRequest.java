package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文档更新请求")
public class DocumentUpdateRequest {

    @Schema(description = "文档ID")
    private Long documentId;

    @Schema(description = "文档标题")
    private String title;
}

