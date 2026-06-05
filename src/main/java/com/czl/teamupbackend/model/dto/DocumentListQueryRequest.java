package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文档列表查询请求")
public class DocumentListQueryRequest {

    @Schema(description = "小组ID")
    private Long teamId;

    @Schema(description = "文档类型：1-资料文档 2-协作文档")
    private Integer type;
}

