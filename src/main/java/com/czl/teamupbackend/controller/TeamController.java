package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.TeamCreateRequest;
import com.czl.teamupbackend.model.dto.TeamDetailRequest;
import com.czl.teamupbackend.model.dto.TeamUpdateRequest;
import com.czl.teamupbackend.model.vo.TeamCreateResponseVO;
import com.czl.teamupbackend.model.vo.TeamDetailVO;
import com.czl.teamupbackend.service.ITeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小组/团队信息控制器
 */
@RestController
@RequestMapping("/team")
@Slf4j
@RequiredArgsConstructor
public class TeamController {

    private final ITeamService teamService;

    @PostMapping("/create")
    public Result<TeamCreateResponseVO> create(@RequestBody TeamCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        TeamCreateResponseVO response = teamService.createTeam(userId, request);
        log.info("Create team endpoint handled, ownerId={}, teamId={}", userId, response.getTeamId());
        return Result.success("创建小组成功", response);
    }

    @PostMapping("/detail")
    public Result<TeamDetailVO> detail(@RequestBody TeamDetailRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        log.info("Team detail endpoint called, userId={}, teamId={}", userId, request == null ? null : request.getTeamId());
        TeamDetailVO response = teamService.getTeamDetail(userId, request);
        return Result.success("查询成功", response);
    }

    @PostMapping("/update")
    public Result<Void> update(@RequestBody TeamUpdateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        teamService.updateTeamInfo(userId, request);
        return Result.success("小组信息更新成功", null);
    }
}
