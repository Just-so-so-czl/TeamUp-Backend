package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务认领请求
 */
@Data
@Schema(description = "任务认领请求")
public class TaskClaimRequest {

    @Schema(description = "任务ID")
    private Long taskId;
}

