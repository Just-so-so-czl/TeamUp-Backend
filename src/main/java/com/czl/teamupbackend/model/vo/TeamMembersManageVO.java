package com.czl.teamupbackend.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小组成员管理列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "小组成员管理列表")
public class TeamMembersManageVO {

    @Schema(description = "当前用户是否为组长")
    private Boolean currentUserCaptain;

    @Schema(description = "当前用户角色名称")
    private String currentUserRoleName;

    @Schema(description = "当前用户角色描述")
    private String currentUserRoleDesc;

    @Schema(description = "待处理入组申请")
    private List<TeamPendingJoinRequestVO> pendingRequests;

    @Schema(description = "已有成员")
    private List<TeamMemberManageItemVO> members;
}
