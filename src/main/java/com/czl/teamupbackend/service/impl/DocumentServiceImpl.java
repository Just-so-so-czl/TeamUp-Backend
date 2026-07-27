package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.mapper.DocumentMapper;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.mapper.UserMapper;
import com.czl.teamupbackend.event.DocumentResourceUploadedEvent;
import com.czl.teamupbackend.model.entity.Document;
import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.model.enums.TeamMemberRoleEnum;
import com.czl.teamupbackend.model.mongo.DocumentContentDoc;
import com.czl.teamupbackend.model.vo.DocumentItemVO;
import com.czl.teamupbackend.model.vo.DocumentListVO;
import com.czl.teamupbackend.model.vo.MentorSidebarDocItemVO; 
import com.czl.teamupbackend.model.vo.MentorSidebarDocListVO;
import com.czl.teamupbackend.model.vo.MentorDocumentMentionVO;
import com.czl.teamupbackend.service.IDocumentService;
import com.czl.teamupbackend.service.IOssService;
import com.czl.teamupbackend.service.CollaborationAgentDocumentGateway;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class  DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements IDocumentService {

    private static final int TYPE_RESOURCE = 1;
    private static final int TYPE_COLLAB = 2;
    private static final String COLLAB_FILE_TYPE = "md";
    private static final String COLLABORATION_DOCUMENT_COLLECTION = "collaboration_documents";
    private static final int MENTION_SEARCH_LIMIT = 8;
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("pdf", "docx", "md", "txt");
    private static final DateTimeFormatter MENTOR_DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final IOssService ossService;
    private final CollaborationAgentDocumentGateway collaborationAgentDocumentGateway;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final MongoTemplate mongoTemplate;

    @Override
    public DocumentListVO listTeamDocuments(Long currentUserId, Long teamId, Integer type) {
        TeamMember member = validateMembership(currentUserId, teamId);
        validateDocumentType(type);

        List<Document> documents = this.list(new LambdaQueryWrapper<Document>()
            .eq(Document::getTeamId, teamId)
            .eq(Document::getType, type)
            .orderByDesc(Document::getCreateTime));

        if (documents.isEmpty()) {
            return DocumentListVO.builder()
                .currentUserCanUpload(canUpload(member, type))
                .documents(Collections.emptyList())
                .build();
        }

        List<Long> creatorIds = documents.stream().map(Document::getCreatorId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, creatorIds))
            .stream()
            .collect(Collectors.toMap(User::getId, user -> user));

        List<DocumentItemVO> items = documents.stream().map(doc -> DocumentItemVO.builder()
            .documentId(doc.getId())
            .teamId(doc.getTeamId())
            .title(doc.getTitle())
            .type(doc.getType())
            .typeName(doc.getType() != null && doc.getType() == TYPE_RESOURCE ? "资料文档" : "协作文档")
            .storagePath(doc.getStoragePath())
            .fileType(doc.getFileType())
            .fileSize(doc.getFileSize())
            .creatorId(doc.getCreatorId())
            .creatorName(resolveUserName(userMap, doc.getCreatorId()))
            .creatorAvatar(resolveUserAvatar(userMap, doc.getCreatorId()))
            .createTime(doc.getCreateTime())
            .updateTime(doc.getUpdateTime())
            .build()).toList();

        return DocumentListVO.builder()
            .currentUserCanUpload(canUpload(member, type))
            .documents(items)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadDocument(Long currentUserId, Long teamId, Integer type, String title, MultipartFile file) {
        TeamMember member = validateMembership(currentUserId, teamId);
        validateDocumentType(type);
        if (!canUpload(member, type)) {
            throw new BizException(403, "当前角色无权限上传该类型文档");
        }
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().trim().isEmpty()) {
            throw new BizException(400, "上传文件不能为空");
        }

        String validTitle = title == null ? "" : title.trim();
        if (validTitle.length() < 1 || validTitle.length() > 200) {
            throw new BizException(400, "文档标题长度需在1到200个字符之间");
        }

        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String ext = getFileExt(originalFilename);
        if (!ALLOWED_FILE_TYPES.contains(ext)) {
            throw new BizException(400, "仅支持pdf/docx/md/txt格式");
        }

        String objectKey = buildObjectKey(teamId, type, ext);
        String storagePath = ossService.upload(objectKey, file);

        Document doc = new Document();
        doc.setTeamId(teamId);
        doc.setTitle(validTitle);
        doc.setType(type);
        doc.setStoragePath(storagePath);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setCreatorId(currentUserId);
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        this.save(doc);
        if (type == TYPE_RESOURCE) {
            applicationEventPublisher.publishEvent(new DocumentResourceUploadedEvent(doc.getId()));
        }

        log.info("Document uploaded, teamId={}, type={}, documentId={}, operatorUserId={}", teamId, type, doc.getId(), currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCollaborationDocument(Long currentUserId, Long teamId, String title) {
        TeamMember member = validateMembership(currentUserId, teamId);
        if (!canUpload(member, TYPE_COLLAB)) {
            throw new BizException(403, "当前角色无权限创建协作文档");
        }

        String validTitle = validateTitle(title);
        LocalDateTime now = LocalDateTime.now();

        Document doc = new Document();
        doc.setTeamId(teamId);
        doc.setTitle(validTitle);
        doc.setType(TYPE_COLLAB);
        doc.setStoragePath("");
        doc.setFileType(COLLAB_FILE_TYPE);
        doc.setFileSize(0L);
        doc.setCreatorId(currentUserId);
        doc.setCreateTime(now);
        doc.setUpdateTime(now);
        this.save(doc);

        log.info("Collaboration document created, teamId={}, documentId={}, operatorUserId={}", teamId, doc.getId(), currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDocumentTitle(Long currentUserId, Long documentId, String title) {
        Document document = getDocumentById(documentId);
        TeamMember member = validateMembership(currentUserId, document.getTeamId());
        if (!canUpload(member, document.getType())) {
            throw new BizException(403, "当前角色无权限修改该类型文档");
        }

        document.setTitle(validateTitle(title));
        document.setUpdateTime(LocalDateTime.now());
        this.updateById(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long currentUserId, Long documentId) {
        Document document = getDocumentById(documentId);
        TeamMember member = validateMembership(currentUserId, document.getTeamId());
        if (!canUpload(member, document.getType())) {
            throw new BizException(403, "当前角色无权限删除该类型文档");
        }
        if (document.getStoragePath() != null && !document.getStoragePath().isBlank()) {
            ossService.delete(document.getStoragePath());
        }
        if (document.getType() != null && document.getType() == TYPE_RESOURCE) {
            long deletedCount = mongoTemplate.remove(
                Query.query(Criteria.where("documentId").is(documentId)),
                DocumentContentDoc.class
            ).getDeletedCount();
            log.info("Resource document content deleted from MongoDB, documentId={}, deletedCount={}, operatorUserId={}",
                documentId, deletedCount, currentUserId);
        } else if (document.getType() != null && document.getType() == TYPE_COLLAB) {
            long deletedCount = mongoTemplate.remove(
                Query.query(Criteria.where("docId").is(String.valueOf(documentId))),
                COLLABORATION_DOCUMENT_COLLECTION
            ).getDeletedCount();
            log.info("Collaboration document content deleted from MongoDB, documentId={}, deletedCount={}, operatorUserId={}",
                documentId, deletedCount, currentUserId);
        }
        this.removeById(documentId);
        log.info("Document deleted, teamId={}, type={}, documentId={}, operatorUserId={}",
            document.getTeamId(), document.getType(), documentId, currentUserId);
    }

    @Override
    public String generateDownloadUrl(Long currentUserId, Long documentId) {
        Document document = getDocumentById(documentId);
        validateMembership(currentUserId, document.getTeamId());
        if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
            throw new BizException(400, "该协作文档内容暂未接入查看或下载");
        }

        String fileName = document.getTitle();
        String fileType = document.getFileType();
        if (fileType != null && !fileType.isBlank() && fileName != null && !fileName.toLowerCase().endsWith("." + fileType.toLowerCase())) {
            fileName = fileName + "." + fileType;
        }
        return ossService.generateDownloadUrl(document.getStoragePath(), fileName);
    }

    @Override
    public byte[] exportCollaborationDocumentPdf(Long currentUserId, Long documentId) {
        Document document = getDocumentById(documentId);
        validateMembership(currentUserId, document.getTeamId());
        if (document.getType() == null || document.getType() != TYPE_COLLAB) {
            throw new BizException(400, "仅协作文档支持导出 PDF");
        }
        byte[] pdf = collaborationAgentDocumentGateway.exportPdf(documentId, document.getTitle());
        log.info("Collaboration document PDF exported, documentId={}, operatorUserId={}, bytes={}",
            documentId, currentUserId, pdf.length);
        return pdf;
    }

    @Override
    public MentorSidebarDocListVO listMentorSidebarDocs(Long currentUserId, Long teamId, Integer type) {
        TeamMember member = validateMembership(currentUserId, teamId);
        validateDocumentType(type);

        List<Document> documents = this.list(new LambdaQueryWrapper<Document>()
            .eq(Document::getTeamId, teamId)
            .eq(Document::getType, type)
            .orderByDesc(Document::getCreateTime));

        if (documents.isEmpty()) {
            return MentorSidebarDocListVO.builder().documents(Collections.emptyList()).build();
        }

        List<Long> creatorIds = documents.stream().map(Document::getCreatorId).distinct().toList();
        Map<Long, String> userNameMap = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, creatorIds))
            .stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));

        List<MentorSidebarDocItemVO> items = documents.stream().map(doc -> MentorSidebarDocItemVO.builder()
            .documentId(String.valueOf(doc.getId()))
            .title(doc.getTitle())
            .creatorName(userNameMap.getOrDefault(doc.getCreatorId(), "未知用户"))
            .dateLabel(doc.getCreateTime() == null ? "--/--" : doc.getCreateTime().format(MENTOR_DATE_FMT))
            .fileType(resolveMentorSidebarFileType(doc))
            .build()).toList();

        return MentorSidebarDocListVO.builder().documents(items).build();
    }

    private String resolveMentorSidebarFileType(Document document) {
        if (document.getType() != null && document.getType() == TYPE_COLLAB) {
            return COLLAB_FILE_TYPE;
        }
        return document.getFileType() == null ? "" : document.getFileType().toLowerCase();
    }

    @Override
    public List<MentorDocumentMentionVO> searchMentorMentionDocuments(Long currentUserId, Long teamId, String keyword) {
        validateMembership(currentUserId, teamId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<Document> query = new LambdaQueryWrapper<Document>()
            .eq(Document::getTeamId, teamId)
            .in(Document::getType, TYPE_RESOURCE, TYPE_COLLAB)
            .orderByDesc(Document::getUpdateTime)
            .last("LIMIT " + MENTION_SEARCH_LIMIT);
        if (!normalizedKeyword.isBlank()) {
            query.like(Document::getTitle, normalizedKeyword);
        }
        return this.list(query).stream().map(document -> MentorDocumentMentionVO.builder()
            .documentId(String.valueOf(document.getId()))
            .title(document.getTitle())
            .type(document.getType())
            .typeName(document.getType() != null && document.getType() == TYPE_RESOURCE ? "资料文档" : "协作文档")
            .fileType(document.getFileType())
            .build()).toList();
    }

    @Override
    public String buildMentorMentionReference(Long currentUserId, Long teamId, List<Long> documentIds) {
        List<Document> documents = resolveMentionDocuments(currentUserId, teamId, documentIds);
        if (documents.isEmpty()) {
            return "";
        }
        return documents.stream()
            .map(document -> "[" + (document.getType() != null && document.getType() == TYPE_RESOURCE ? "资料" : "协作")
                + "文档：" + document.getTitle() + "]")
            .collect(Collectors.joining("、"));
    }

    @Override
    public String buildMentorMentionContext(Long currentUserId, Long teamId, List<Long> documentIds) {
        List<Document> documents = resolveMentionDocuments(currentUserId, teamId, documentIds);
        if (documents.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("\n\n以下是用户在本轮明确引用的文档全文。仅将其用于回答当前问题，不要复述全文或泄露无关内容：\n");
        for (Document document : documents) {
            String text = loadFullMentionText(document);
            if (text.isBlank()) {
                throw new BizException(409, "引用文档《" + document.getTitle() + "》的正文暂不可用，请稍后重试");
            }
            context.append("\n--- 文档：").append(document.getTitle())
                .append("（").append(document.getType() != null && document.getType() == TYPE_RESOURCE ? "资料文档" : "协作文档")
                .append("）---\n").append(text).append("\n--- 文档结束 ---\n");
        }
        return context.toString();
    }

    private List<Document> resolveMentionDocuments(Long currentUserId, Long teamId, List<Long> documentIds) {
        validateMembership(currentUserId, teamId);
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = documentIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<Document> documents = this.list(new LambdaQueryWrapper<Document>()
            .eq(Document::getTeamId, teamId)
            .in(Document::getId, normalizedIds)
            .in(Document::getType, TYPE_RESOURCE, TYPE_COLLAB));
        if (documents.size() != normalizedIds.size()) {
            throw new BizException(403, "存在不可引用的文档");
        }
        Map<Long, Document> documentMap = documents.stream().collect(Collectors.toMap(Document::getId, document -> document));
        return normalizedIds.stream().map(documentMap::get).toList();
    }

    private String loadFullMentionText(Document document) {
        if (document.getType() != null && document.getType() == TYPE_RESOURCE) {
            DocumentContentDoc content = mongoTemplate.findOne(
                Query.query(Criteria.where("documentId").is(document.getId())), DocumentContentDoc.class);
            return content == null || content.getExtractedText() == null ? "" : content.getExtractedText().trim();
        }
        org.bson.Document content = mongoTemplate.findOne(
            Query.query(Criteria.where("docId").is(String.valueOf(document.getId()))),
            org.bson.Document.class, COLLABORATION_DOCUMENT_COLLECTION);
        return content == null || content.getString("plain_text") == null ? "" : content.getString("plain_text").trim();
    }

    private Document getDocumentById(Long documentId) {
        if (documentId == null || documentId <= 0) {
            throw new BizException(400, "文档ID不合法");
        }
        Document document = this.getById(documentId);
        if (document == null) {
            throw new BizException(404, "文档不存在");
        }
        return document;
    }

    private TeamMember validateMembership(Long currentUserId, Long teamId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (teamId == null || teamId <= 0) {
            throw new BizException(400, "小组ID不合法");
        }

        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BizException(404, "小组不存在");
        }

        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, teamId)
            .eq(TeamMember::getUserId, currentUserId)
            .last("limit 1"));

        if (member == null) {
            throw new BizException(403, "你不是该小组成员");
        }
        return member;
    }

    private void validateDocumentType(Integer type) {
        if (type == null || (type != TYPE_RESOURCE && type != TYPE_COLLAB)) {
            throw new BizException(400, "文档类型不合法");
        }
    }

    private boolean canUpload(TeamMember member, Integer type) {
        if (member == null) {
            return false;
        }
        if (type != null && type == TYPE_RESOURCE) {
            return true;
        }
        TeamMemberRoleEnum role = TeamMemberRoleEnum.fromCode(member.getRole());
        return role == TeamMemberRoleEnum.CAPTAIN || role == TeamMemberRoleEnum.LEADER;
    }

    private String validateTitle(String title) {
        String validTitle = title == null ? "" : title.trim();
        if (validTitle.length() < 1 || validTitle.length() > 200) {
            throw new BizException(400, "文档标题长度需在1到200个字符之间");
        }
        return validTitle;
    }

    private String resolveUserName(Map<Long, User> userMap, Long userId) {
        User user = userMap.get(userId);
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return "未知用户";
        }
        return user.getUsername();
    }

    private Integer resolveUserAvatar(Map<Long, User> userMap, Long userId) {
        User user = userMap.get(userId);
        if (user == null || user.getAvatar() == null) {
            return 1;
        }
        return user.getAvatar();
    }

    private String buildObjectKey(Long teamId, Integer type, String ext) {
        String typeName = type != null && type == TYPE_RESOURCE ? "resource" : "collaboration";
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "team/" + teamId + "/" + typeName + "/" + ts + "-" + System.nanoTime() + "." + ext;
    }

    private String getFileExt(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }
}
