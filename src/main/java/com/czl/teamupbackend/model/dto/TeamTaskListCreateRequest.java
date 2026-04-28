package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 小组任务清单创建请求
 */
@Data
@Schema(description = "小组任务清单创建请求")
public class TeamTaskListCreateRequest {

    @Schema(description = "小组ID")
    private Long teamId;

    @Schema(description = "清单标题")
    private String title;

    @Schema(description = "清单描述")
    private String description;

    @Schema(description = "清单截止时间")
    private LocalDateTime deadline;
}

