package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.CollaborationDocumentSummaryRequest;
import com.czl.teamupbackend.model.vo.CollaborationDocumentSummaryVO;
import com.czl.teamupbackend.service.CollaborationDocumentSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/document/collaboration-summary")
@RequiredArgsConstructor
public class CollaborationDocumentSummaryController {

    private final CollaborationDocumentSummaryService collaborationDocumentSummaryService;

    @PostMapping("/generate")
    public Result<CollaborationDocumentSummaryVO> generate(@RequestBody CollaborationDocumentSummaryRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getDocumentId() == null) {
            throw new BizException(400, "文档ID不能为空");
        }
        return Result.success(collaborationDocumentSummaryService.requestManualSummary(userId, request.getDocumentId()));
    }

    @PostMapping("/status")
    public Result<CollaborationDocumentSummaryVO> status(@RequestBody CollaborationDocumentSummaryRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getDocumentId() == null) {
            throw new BizException(400, "文档ID不能为空");
        }
        return Result.success(collaborationDocumentSummaryService.getSummaryStatus(userId, request.getDocumentId()));
    }
}
