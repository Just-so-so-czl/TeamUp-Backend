package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小组任务清单查询请求
 */
@Data
@Schema(description = "小组任务清单查询请求")
public class TeamTaskListQueryRequest {

    @Schema(description = "小组ID")
    private Long teamId;
}

