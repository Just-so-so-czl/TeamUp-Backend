package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.TeamMemberRemoveRequest;
import com.czl.teamupbackend.model.dto.TeamMemberRoleUpdateRequest;
import com.czl.teamupbackend.model.dto.TeamMembersQueryRequest;
import com.czl.teamupbackend.model.dto.TeamSelfRoleDescUpdateRequest;
import com.czl.teamupbackend.model.vo.MyTeamListVO;
import com.czl.teamupbackend.model.vo.TeamMembersManageVO;
import com.czl.teamupbackend.service.ITeamMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小组成员控制器
 */
@RestController
@RequestMapping("/team-member")
@Slf4j
@RequiredArgsConstructor
public class TeamMemberController {

    private final ITeamMemberService teamMemberService;

    @PostMapping("/my-teams")
    public Result<MyTeamListVO> myTeams(@RequestBody(required = false) Object ignored) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        MyTeamListVO response = teamMemberService.listMyTeams(userId);
        log.info("My teams endpoint handled, userId={}, teamCount={}",
            userId, response.getTeams() == null ? 0 : response.getTeams().size());
        return Result.success("查询成功", response);
    }

    @PostMapping("/team-manage-list")
    public Result<TeamMembersManageVO> teamManageList(@RequestBody TeamMembersQueryRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null) {
            throw new BizException(400, "小组ID不能为空");
        }
        TeamMembersManageVO response = teamMemberService.getTeamMembersManage(userId, request.getTeamId());
        return Result.success("查询成功", response);
    }

    @PostMapping("/update-role")
    public Result<Void> updateRole(@RequestBody TeamMemberRoleUpdateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null || request.getMemberUserId() == null || request.getRoleCode() == null) {
            throw new BizException(400, "参数不完整");
        }
        teamMemberService.updateMemberRole(userId, request.getTeamId(), request.getMemberUserId(), request.getRoleCode());
        return Result.success("角色修改成功", null);
    }

    @PostMapping("/remove")
    public Result<Void> removeMember(@RequestBody TeamMemberRemoveRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null || request.getMemberUserId() == null) {
            throw new BizException(400, "参数不完整");
        }
        teamMemberService.removeMember(userId, request.getTeamId(), request.getMemberUserId());
        return Result.success("成员移除成功", null);
    }

    @PostMapping("/update-self-role-desc")
    public Result<Void> updateSelfRoleDesc(@RequestBody TeamSelfRoleDescUpdateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null) {
            throw new BizException(400, "参数不完整");
        }
        teamMemberService.updateSelfRoleDesc(userId, request.getTeamId(), request.getRoleDesc());
        return Result.success("角色描述更新成功", null);
    }
}
