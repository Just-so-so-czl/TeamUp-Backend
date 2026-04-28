package com.czl.teamupbackend.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 任务清单信息
 */
@Data
@Builder
@Schema(description = "任务清单信息")
public class TeamTaskListItemVO {

    @Schema(description = "任务清单ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskListId;

    @Schema(description = "清单标题")
    private String title;

    @Schema(description = "清单描述")
    private String description;

    @Schema(description = "创建者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long creatorId;

    @Schema(description = "创建者名称")
    private String creatorName;

    @Schema(description = "清单截止时间")
    private LocalDateTime deadline;

    @Schema(description = "清单任务项")
    private List<TeamTaskItemVO> tasks;
}

