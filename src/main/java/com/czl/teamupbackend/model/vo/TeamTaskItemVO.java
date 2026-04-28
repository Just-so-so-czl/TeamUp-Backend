package com.czl.teamupbackend.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 任务项信息
 */
@Data
@Builder
@Schema(description = "任务项信息")
public class TeamTaskItemVO {

    @Schema(description = "任务ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "任务状态: 0-待办, 1-完成")
    private Integer status;

    @Schema(description = "任务截止时间")
    private LocalDateTime deadline;

    @Schema(description = "任务完成备注")
    private String completionNote;

    @Schema(description = "任务负责人")
    private List<TeamTaskAssigneeVO> assignees;
}

