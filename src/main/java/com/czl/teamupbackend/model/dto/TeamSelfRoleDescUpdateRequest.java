package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前用户更新自己在小组中的角色描述
 */
@Data
@Schema(description = "当前用户更新自己在小组中的角色描述")
public class TeamSelfRoleDescUpdateRequest {

    @Schema(description = "小组ID")
    private Long teamId;

    @Schema(description = "角色描述")
    private String roleDesc;
}

