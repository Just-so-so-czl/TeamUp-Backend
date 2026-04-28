package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小组任务清单删除请求
 */
@Data
@Schema(description = "小组任务清单删除请求")
public class TeamTaskListDeleteRequest {

    @Schema(description = "任务清单ID")
    private Long taskListId;
}

