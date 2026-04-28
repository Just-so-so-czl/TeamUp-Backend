package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务创建请求
 */
@Data
@Schema(description = "任务创建请求")
public class TeamTaskCreateRequest {

    @Schema(description = "任务清单ID")
    private Long taskListId;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "任务截止时间")
    private LocalDateTime deadline;
}

