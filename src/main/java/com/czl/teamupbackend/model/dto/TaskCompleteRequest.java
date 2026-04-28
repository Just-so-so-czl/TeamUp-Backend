package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务完成请求
 */
@Data
@Schema(description = "任务完成请求")
public class TaskCompleteRequest {

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "完成备注")
    private String completionNote;
}

