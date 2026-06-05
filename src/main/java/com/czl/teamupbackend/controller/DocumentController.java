package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.DocumentDeleteRequest;
import com.czl.teamupbackend.model.dto.DocumentCollabCreateRequest;
import com.czl.teamupbackend.model.dto.DocumentDownloadRequest;
import com.czl.teamupbackend.model.dto.DocumentListQueryRequest;
import com.czl.teamupbackend.model.dto.DocumentUpdateRequest;
import com.czl.teamupbackend.model.dto.DocumentUploadMetaRequest;
import com.czl.teamupbackend.model.dto.MentorSidebarDocRequest;
import com.czl.teamupbackend.model.vo.DocumentListVO;
import com.czl.teamupbackend.model.vo.MentorSidebarDocListVO;
import com.czl.teamupbackend.service.IDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/document")
@Slf4j
@RequiredArgsConstructor
public class DocumentController {

    private final IDocumentService documentService;

    @PostMapping("/list")
    public Result<DocumentListVO> list(@RequestBody DocumentListQueryRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null || request.getType() == null) {
            throw new BizException(400, "参数不完整");
        }
        return Result.success("查询成功", documentService.listTeamDocuments(userId, request.getTeamId(), request.getType()));
    }

    @PostMapping("/mentor-sidebar-list")
    public Result<MentorSidebarDocListVO> mentorSidebarList(@RequestBody MentorSidebarDocRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null || request.getType() == null) {
            throw new BizException(400, "参数不完整");
        }
        return Result.success(documentService.listMentorSidebarDocs(userId, request.getTeamId(), request.getType()));
    }

    @PostMapping("/upload")
    public Result<Void> upload(@ModelAttribute DocumentUploadMetaRequest request, @RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null || request.getType() == null) {
            throw new BizException(400, "参数不完整");
        }
        documentService.uploadDocument(userId, request.getTeamId(), request.getType(), request.getTitle(), file);
        return Result.success("上传成功", null);
    }

    @PostMapping("/create-collab")
    public Result<Void> createCollab(@RequestBody DocumentCollabCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null) {
            throw new BizException(400, "参数不完整");
        }
        documentService.createCollaborationDocument(userId, request.getTeamId(), request.getTitle());
        return Result.success("创建成功", null);
    }

    @PostMapping("/update")
    public Result<Void> update(@RequestBody DocumentUpdateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getDocumentId() == null) {
            throw new BizException(400, "文档ID不能为空");
        }
        documentService.updateDocumentTitle(userId, request.getDocumentId(), request.getTitle());
        return Result.success("更新成功", null);
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody DocumentDeleteRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getDocumentId() == null) {
            throw new BizException(400, "文档ID不能为空");
        }
        documentService.deleteDocument(userId, request.getDocumentId());
        return Result.success("删除成功", null);
    }

    @PostMapping("/download-url")
    public Result<String> downloadUrl(@RequestBody DocumentDownloadRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getDocumentId() == null) {
            throw new BizException(400, "文档ID不能为空");
        }
        String url = documentService.generateDownloadUrl(userId, request.getDocumentId());
        return Result.success("查询成功", url);
    }
}
