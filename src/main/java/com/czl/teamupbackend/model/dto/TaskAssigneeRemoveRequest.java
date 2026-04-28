package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 移除任务负责人请求
 */
@Data
@Schema(description = "移除任务负责人请求")
public class TaskAssigneeRemoveRequest {

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "目标成员用户ID")
    private Long assigneeUserId;
}

