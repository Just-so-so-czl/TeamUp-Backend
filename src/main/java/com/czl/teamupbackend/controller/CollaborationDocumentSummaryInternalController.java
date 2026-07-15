package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.CollaborationDocumentSummaryRequest;
import com.czl.teamupbackend.service.CollaborationDocumentSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/collaboration-summary")
@RequiredArgsConstructor
public class CollaborationDocumentSummaryInternalController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Collaboration-Internal-Token";

    private final CollaborationDocumentSummaryService collaborationDocumentSummaryService;

    @Value("${collaboration-summary.internal-token}")
    private String internalToken;

    @PostMapping("/content-changed")
    public Result<Void> contentChanged(
        @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String requestToken,
        @RequestBody CollaborationDocumentSummaryRequest request
    ) {
        if (!StringUtils.hasText(internalToken) || !internalToken.equals(requestToken)) {
            throw new BizException(403, "协同服务内部凭证无效");
        }
        if (request == null || request.getDocumentId() == null || request.getDocumentId() <= 0) {
            throw new BizException(400, "文档ID不合法");
        }
        collaborationDocumentSummaryService.recordDocumentChanged(String.valueOf(request.getDocumentId()));
        return Result.success(null);
    }
}
