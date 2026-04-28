package com.czl.teamupbackend.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 小组成员管理项
 */
@Data
@Builder
@Schema(description = "小组成员管理项")
public class TeamMemberManageItemVO {

    @Schema(description = "成员用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "成员名称")
    private String username;

    @Schema(description = "角色编码")
    private Integer roleCode;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色描述")
    private String roleDesc;

    @Schema(description = "头像编号")
    private Integer avatar;

    @Schema(description = "加入时间")
    private LocalDateTime joinTime;
}
