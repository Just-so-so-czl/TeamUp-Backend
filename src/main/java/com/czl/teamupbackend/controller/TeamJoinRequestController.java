package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.TeamJoinRequestReviewRequest;
import com.czl.teamupbackend.model.dto.TeamJoinRequestSubmitRequest;
import com.czl.teamupbackend.service.ITeamJoinRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入组申请控制器
 */
@RestController
@RequestMapping("/team-join-request")
@Slf4j
@RequiredArgsConstructor
public class TeamJoinRequestController {

    private final ITeamJoinRequestService teamJoinRequestService;

    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody TeamJoinRequestSubmitRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        teamJoinRequestService.submitJoinRequest(userId, request);
        log.info("Join request submit endpoint handled, userId={}", userId);
        return Result.success("申请已提交，请等待组长处理", null);
    }

    @PostMapping("/approve")
    public Result<Void> approve(@RequestBody TeamJoinRequestReviewRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getRequestId() == null) {
            throw new BizException(400, "申请ID不能为空");
        }
        teamJoinRequestService.approveJoinRequest(userId, request.getRequestId());
        return Result.success("已同意加入申请", null);
    }

    @PostMapping("/reject")
    public Result<Void> reject(@RequestBody TeamJoinRequestReviewRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getRequestId() == null) {
            throw new BizException(400, "申请ID不能为空");
        }
        teamJoinRequestService.rejectJoinRequest(userId, request.getRequestId());
        return Result.success("已拒绝加入申请", null);
    }
}

