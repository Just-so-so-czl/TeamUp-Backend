package com.czl.teamupbackend.controller;


import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.TeamCreateRequest;
import com.czl.teamupbackend.model.vo.TeamCreateResponseVO;
import com.czl.teamupbackend.service.ITeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 小组/团队信息表 前端控制器
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@RestController
@RequestMapping("/team")
@Slf4j
@RequiredArgsConstructor
public class TeamController {

    private final ITeamService teamService;

    @PostMapping("/create")
    public Result<TeamCreateResponseVO> create(@RequestBody TeamCreateRequest request) {
        TeamCreateResponseVO response = teamService.createTeam(request);
        log.info("Create team endpoint handled, ownerId={}, teamId={}", request.getUserId(), response.getTeamId());
        return Result.success("创建小组成功", response);
    }
}
