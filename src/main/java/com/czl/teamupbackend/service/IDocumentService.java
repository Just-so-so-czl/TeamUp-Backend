package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.vo.MentorSidebarDocListVO;
import com.czl.teamupbackend.model.entity.Document;
import com.czl.teamupbackend.model.vo.DocumentListVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import com.czl.teamupbackend.model.vo.MentorDocumentMentionVO;

/**
 * <p>
 * 小组知识库文档元数据表 服务类
 * </p>
 *
 * @author czl
 * @since 2026-04-28
 */
public interface IDocumentService extends IService<Document> {

    DocumentListVO listTeamDocuments(Long currentUserId, Long teamId, Integer type);

    void uploadDocument(Long currentUserId, Long teamId, Integer type, String title, MultipartFile file);

    void createCollaborationDocument(Long currentUserId, Long teamId, String title);

    void updateDocumentTitle(Long currentUserId, Long documentId, String title);

    void deleteDocument(Long currentUserId, Long documentId);

    String generateDownloadUrl(Long currentUserId, Long documentId);

    MentorSidebarDocListVO listMentorSidebarDocs(Long currentUserId, Long teamId, Integer type);

    List<MentorDocumentMentionVO> searchMentorMentionDocuments(Long currentUserId, Long teamId, String keyword);

    String buildMentorMentionContext(Long currentUserId, Long teamId, List<Long> documentIds);

    String buildMentorMentionReference(Long currentUserId, Long teamId, List<Long> documentIds);
}
