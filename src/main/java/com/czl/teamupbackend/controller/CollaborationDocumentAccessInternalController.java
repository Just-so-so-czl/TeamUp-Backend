package com.czl.teamupbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.jwt.JwtTokenUtil;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.mapper.DocumentMapper;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.model.dto.CollaborationDocumentAccessVerifyRequest;
import com.czl.teamupbackend.model.entity.Document;
import com.czl.teamupbackend.model.entity.TeamMember;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal collaboration handshake authorization endpoint. */
@RestController
@RequestMapping("/internal/collaboration-access")
@RequiredArgsConstructor
public class CollaborationDocumentAccessInternalController {

    private static final String INTERNAL_TOKEN_HEADER = "X-Collaboration-Access-Token";
    private static final int COLLABORATION_DOCUMENT_TYPE = 2;

    private final JwtTokenUtil jwtTokenUtil;
    private final DocumentMapper documentMapper;
    private final TeamMemberMapper teamMemberMapper;

    @Value("${collaboration-access.internal-token}")
    private String internalToken;

    @PostMapping("/verify")
    public Result<Boolean> verify(
        @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String requestToken,
        @RequestBody CollaborationDocumentAccessVerifyRequest request
    ) {
        if (!StringUtils.hasText(internalToken) || !internalToken.equals(requestToken)) {
            throw new BizException(403, "协同访问内部凭证无效");
        }
        if (request == null || !StringUtils.hasText(request.getDocumentId()) || !StringUtils.hasText(request.getToken())) {
            throw new BizException(400, "协同访问校验参数不完整");
        }
        Long documentId;
        Long userId;
        try {
            documentId = Long.valueOf(request.getDocumentId().trim());
            userId = jwtTokenUtil.getUserId(request.getToken().trim());
        } catch (Exception exception) {
            throw new BizException(403, "协同访问令牌无效");
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null || !Integer.valueOf(COLLABORATION_DOCUMENT_TYPE).equals(document.getType())) {
            throw new BizException(403, "无权访问该协作文档");
        }
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, document.getTeamId())
            .eq(TeamMember::getUserId, userId)
            .last("LIMIT 1"));
        if (member == null) {
            throw new BizException(403, "无权访问该协作文档");
        }
        return Result.success(Boolean.TRUE);
    }
}
