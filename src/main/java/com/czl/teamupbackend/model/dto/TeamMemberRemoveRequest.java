package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小组成员移除请求
 */
@Data
@Schema(description = "小组成员移除请求")
public class TeamMemberRemoveRequest {

    @Schema(description = "小组ID")
    private Long teamId;

    @Schema(description = "成员用户ID")
    private Long memberUserId;
}

