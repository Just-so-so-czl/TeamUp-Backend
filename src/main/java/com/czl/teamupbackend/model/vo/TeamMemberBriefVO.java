package com.czl.teamupbackend.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 小组成员简要信息
 */
@Data
@Builder
@Schema(description = "小组成员简要信息")
public class TeamMemberBriefVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "角色编码: 1-Captain, 2-Leader, 3-Member")
    private Integer roleCode;

    @Schema(description = "角色名称（首字母大写）")
    private String roleName;
}

