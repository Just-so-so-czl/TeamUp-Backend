package com.czl.teamupbackend.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 任务负责人信息
 */
@Data
@Builder
@Schema(description = "任务负责人信息")
public class TeamTaskAssigneeVO {

    @Schema(description = "负责人用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "负责人名称")
    private String username;
}

