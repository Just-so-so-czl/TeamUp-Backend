package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "协作文档 PDF 导出请求")
public class DocumentPdfExportRequest {

    @Schema(description = "协作文档 ID")
    private Long documentId;
}
