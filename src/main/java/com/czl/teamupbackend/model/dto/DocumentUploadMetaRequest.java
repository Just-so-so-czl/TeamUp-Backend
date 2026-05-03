package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文档上传元信息请求")
public class DocumentUploadMetaRequest {

    @Schema(description = "小组ID")
    private Long teamId;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档类型：1-资料文档 3-Agent文档")
    private Integer type;
}

