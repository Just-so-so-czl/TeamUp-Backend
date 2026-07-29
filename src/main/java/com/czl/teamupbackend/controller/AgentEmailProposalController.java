package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.AgentEmailProposalExecuteRequest;
import com.czl.teamupbackend.model.dto.AgentEmailProposalQueryRequest;
import com.czl.teamupbackend.model.dto.AgentProposalRejectRequest;
import com.czl.teamupbackend.model.vo.AgentEmailProposalVO;
import com.czl.teamupbackend.service.AgentEmailProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/mentor/email-proposal")
@RequiredArgsConstructor
public class AgentEmailProposalController {
    private final AgentEmailProposalService proposalService;
    @PostMapping("/pending")
    public Result<AgentEmailProposalVO> pending(@RequestBody AgentEmailProposalQueryRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) throw new BizException(401, "未登录");
        if (request == null || request.getRunId() == null) throw new BizException(400, "runId不能为空");
        return Result.success(proposalService.getPending(userId, request.getRunId()));
    }
    @PostMapping("/execute")
    public Result<AgentEmailProposalVO> execute(@RequestBody AgentEmailProposalExecuteRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) throw new BizException(401, "未登录");
        if (request == null || request.getDraftId() == null) throw new BizException(400, "draftId不能为空");
        return Result.success(proposalService.execute(userId, request.getDraftId(), request.getSubject(), request.getContent()));
    }
    @PostMapping("/reject")
    public Result<AgentEmailProposalVO> reject(@RequestBody AgentProposalRejectRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) throw new BizException(401, "未登录");
        if (request == null || request.getDraftId() == null) throw new BizException(400, "draftId不能为空");
        return Result.success(proposalService.reject(userId, request.getDraftId()));
    }
}
