package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.model.vo.MyTeamListVO;
import com.czl.teamupbackend.model.vo.TeamMembersManageVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 小组库成员关联表 服务类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
public interface ITeamMemberService extends IService<TeamMember> {

    /**
     * 查询当前用户加入的小组列表
     *
     * @param userId 用户ID
     * @return 小组列表
     */
    MyTeamListVO listMyTeams(Long userId);

    /**
     * 查询小组成员管理信息（待审批申请 + 已有成员）
     *
     * @param currentUserId 当前用户ID
     * @param teamId 小组ID
     * @return 成员管理列表
     */
    TeamMembersManageVO getTeamMembersManage(Long currentUserId, Long teamId);

    /**
     * 组长修改成员角色
     *
     * @param currentUserId 当前用户ID
     * @param teamId 小组ID
     * @param memberUserId 成员用户ID
     * @param roleCode 目标角色编码
     */
    void updateMemberRole(Long currentUserId, Long teamId, Long memberUserId, Integer roleCode);

    /**
     * 组长移除成员
     *
     * @param currentUserId 当前用户ID
     * @param teamId 小组ID
     * @param memberUserId 成员用户ID
     */
    void removeMember(Long currentUserId, Long teamId, Long memberUserId);

    /**
     * 更新当前用户在小组中的角色描述
     *
     * @param currentUserId 当前用户ID
     * @param teamId 小组ID
     * @param roleDesc 角色描述
     */
    void updateSelfRoleDesc(Long currentUserId, Long teamId, String roleDesc);
}
