package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.AgentCollaborationDocumentPatchExecuteRequest;
import com.czl.teamupbackend.model.dto.AgentEmailProposalQueryRequest;
import com.czl.teamupbackend.model.dto.AgentProposalRejectRequest;
import com.czl.teamupbackend.model.vo.AgentCollaborationDocumentPatchProposalVO;
import com.czl.teamupbackend.service.AgentCollaborationDocumentProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/mentor/collaboration-document-proposal")
@RequiredArgsConstructor
public class AgentCollaborationDocumentProposalController {

    private final AgentCollaborationDocumentProposalService proposalService;

    @PostMapping("/pending")
    public Result<AgentCollaborationDocumentPatchProposalVO> pending(@RequestBody AgentEmailProposalQueryRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getRunId() == null) {
            throw new BizException(400, "runId不能为空");
        }
        return Result.success(proposalService.getPending(userId, request.getRunId()));
    }

    @PostMapping("/execute")
    public Result<AgentCollaborationDocumentPatchProposalVO> execute(
        @RequestBody AgentCollaborationDocumentPatchExecuteRequest request
    ) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getDraftId() == null) {
            throw new BizException(400, "draftId不能为空");
        }
        return Result.success(proposalService.execute(userId, request.getDraftId()));
    }

    @PostMapping("/reject")
    public Result<AgentCollaborationDocumentPatchProposalVO> reject(@RequestBody AgentProposalRejectRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getDraftId() == null) {
            throw new BizException(400, "draftId不能为空");
        }
        return Result.success(proposalService.reject(userId, request.getDraftId()));
    }
}
