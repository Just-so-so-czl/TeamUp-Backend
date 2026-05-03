package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文档下载请求")
public class DocumentDownloadRequest {

    @Schema(description = "文档ID")
    private Long documentId;
}

