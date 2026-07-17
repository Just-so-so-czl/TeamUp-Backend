package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.AgentEmailProposalQueryRequest;
import com.czl.teamupbackend.model.dto.AgentTaskListProposalExecuteRequest;
import com.czl.teamupbackend.model.vo.AgentTaskListProposalVO;
import com.czl.teamupbackend.service.AgentTaskListProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/mentor/task-list-proposal")
@RequiredArgsConstructor
public class AgentTaskListProposalController {
    private final AgentTaskListProposalService proposalService;
    @PostMapping("/pending") public Result<AgentTaskListProposalVO> pending(@RequestBody AgentEmailProposalQueryRequest request) { Long userId = UserContext.getCurrentUserId(); if (userId == null) throw new BizException(401, "未登录"); if (request == null || request.getRunId() == null) throw new BizException(400, "runId不能为空"); return Result.success(proposalService.getPending(userId, request.getRunId())); }
    @PostMapping("/execute") public Result<AgentTaskListProposalVO> execute(@RequestBody AgentTaskListProposalExecuteRequest request) { Long userId = UserContext.getCurrentUserId(); if (userId == null) throw new BizException(401, "未登录"); if (request == null || request.getDraftId() == null) throw new BizException(400, "draftId不能为空"); return Result.success(proposalService.execute(userId, request.getDraftId(), request.getTitle(), request.getDescription(), request.getDeadline(), request.getTaskDescriptions())); }
}
