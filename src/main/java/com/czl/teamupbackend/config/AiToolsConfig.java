package com.czl.teamupbackend.config;

import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.service.AgentRunService;
import com.czl.teamupbackend.mapper.DocumentMapper;
import com.czl.teamupbackend.model.dto.AiDocumentFullTextToolRequest;
import com.czl.teamupbackend.model.dto.AiEmailProposalToolRequest;
import com.czl.teamupbackend.model.dto.AiTeamDocumentsToolRequest;
import com.czl.teamupbackend.model.dto.TeamIdToolRequest;
import com.czl.teamupbackend.model.dto.TeamDetailRequest;
import com.czl.teamupbackend.model.mongo.DocumentContentDoc;
import com.czl.teamupbackend.model.entity.Document;
import com.czl.teamupbackend.model.vo.DocumentItemVO;
import com.czl.teamupbackend.model.vo.DocumentListVO;
import com.czl.teamupbackend.model.vo.TeamDetailVO;
import com.czl.teamupbackend.model.vo.TeamMemberManageItemVO;
import com.czl.teamupbackend.model.vo.TeamMembersManageVO;
import com.czl.teamupbackend.model.vo.TeamTaskItemVO;
import com.czl.teamupbackend.model.vo.TeamTaskListItemVO;
import com.czl.teamupbackend.model.vo.TeamTaskListVO;
import com.czl.teamupbackend.service.IDocumentService;
import com.czl.teamupbackend.service.ITaskListService;
import com.czl.teamupbackend.service.ITeamMemberService;
import com.czl.teamupbackend.service.ITeamService;
import com.czl.teamupbackend.service.TeamWorkProfileService;
import com.czl.teamupbackend.service.AgentEmailProposalService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@Configuration
public class AiToolsConfig {

    private static final String TOOL_CTX_USER_ID = "userId";
    private static final String TOOL_CTX_TEAM_ID = "teamId";
    private static final String TOOL_CTX_AGENT_RUN_ID = "agentRunId";
    private static final int DOCUMENT_TYPE_RESOURCE = 1;
    private static final int DOCUMENT_TYPE_COLLAB = 2;

    private void recordReadTool(AgentRunService agentRunService, ToolContext toolContext, String toolName, String summary) {
        Long runId = getLongValue(toolContext, TOOL_CTX_AGENT_RUN_ID);
        if (runId != null) {
            agentRunService.recordReadTool(runId, toolName, summary);
        }
    }

    @Bean
    @Description("获取当前小组的整体概览信息，包括小组基础信息、成员数量、任务数量、未完成任务数量、文档数量和当前用户在小组中的角色")
    public BiFunction<TeamIdToolRequest, ToolContext, Map<String, Object>> queryTeamOverview(
        ITeamService teamService,
        ITeamMemberService teamMemberService,
        ITaskListService taskListService,
        IDocumentService documentService,
        AgentRunService agentRunService
    ) {
        return (request, toolContext) -> {
            recordReadTool(agentRunService, toolContext, "queryTeamOverview", "正在查询小组概览");
            ToolIdentity identity = resolveIdentity(request, toolContext, "查询小组概览失败");
            try {
                teamService.validateTeamAccessible(identity.userId(), identity.teamId());
            } catch (BizException e) {
                if (isInvalidTeamContext(e)) {
                    log.warn("AI tool queryTeamOverview skipped due to invalid team context, userId={}, teamId={}, message={}",
                        identity.userId(), identity.teamId(), e.getMessage());
                    return buildInvalidTeamOverview(identity.teamId(), e.getMessage());
                }
                throw e;
            }
            TeamDetailVO detail = teamService.getTeamDetail(identity.userId(), buildTeamDetailRequest(identity.teamId()));
            TeamMembersManageVO members = teamMemberService.getTeamMembersManage(identity.userId(), identity.teamId());
            TeamTaskListVO taskLists = taskListService.listTeamTaskLists(identity.userId(), identity.teamId());
            DocumentListVO resourceDocs = documentService.listTeamDocuments(identity.userId(), identity.teamId(), DOCUMENT_TYPE_RESOURCE);
            DocumentListVO collabDocs = documentService.listTeamDocuments(identity.userId(), identity.teamId(), DOCUMENT_TYPE_COLLAB);

            Map<String, Object> overview = new HashMap<>();
            overview.put("team", Map.of(
                "teamId", detail.getTeamId(),
                "teamName", detail.getTeamName(),
                "description", detail.getDescription(),
                "createTime", detail.getCreateTime(),
                "currentUserCaptain", detail.getCurrentUserCaptain()
            ));
            overview.put("currentUserRole", Map.of(
                "roleName", members.getCurrentUserRoleName(),
                "roleDesc", members.getCurrentUserRoleDesc()
            ));
            overview.put("memberCount", members.getMembers() == null ? 0 : members.getMembers().size());
            overview.put("taskListCount", taskLists.getTaskLists() == null ? 0 : taskLists.getTaskLists().size());
            overview.put("taskCount", countTasks(taskLists));
            overview.put("unfinishedTaskCount", countUnfinishedTasks(taskLists));
            overview.put("resourceDocumentCount", resourceDocs.getDocuments() == null ? 0 : resourceDocs.getDocuments().size());
            overview.put("collaborationDocumentCount", collabDocs.getDocuments() == null ? 0 : collabDocs.getDocuments().size());
            return overview;
        };
    }

    @Bean
    @Description("获取当前小组成员信息，包括成员姓名、角色、职责描述、加入时间，以及当前用户在小组中的角色")
    public BiFunction<TeamIdToolRequest, ToolContext, Map<String, Object>> queryTeamMembers(
        ITeamMemberService teamMemberService,
        ITeamService teamService,
        AgentRunService agentRunService
    ) {
        return (request, toolContext) -> {
            recordReadTool(agentRunService, toolContext, "queryTeamMembers", "正在查询成员与角色信息");
            ToolIdentity identity = resolveIdentity(request, toolContext, "查询小组成员失败");
            try {
                teamService.validateTeamAccessible(identity.userId(), identity.teamId());
            } catch (BizException e) {
                if (isInvalidTeamContext(e)) {
                    log.warn("AI tool queryTeamMembers skipped due to invalid team context, userId={}, teamId={}, message={}",
                        identity.userId(), identity.teamId(), e.getMessage());
                    return buildInvalidTeamMembers(identity.teamId(), e.getMessage());
                }
                throw e;
            }
            TeamMembersManageVO members = teamMemberService.getTeamMembersManage(identity.userId(), identity.teamId());
            Map<String, Object> result = new HashMap<>();
            result.put("currentUserRoleName", members.getCurrentUserRoleName());
            result.put("currentUserRoleDesc", members.getCurrentUserRoleDesc());
            result.put("currentUserCaptain", members.getCurrentUserCaptain());
            result.put("members", sanitizeMembers(members.getMembers()));
            return result;
        };
    }

    @Bean
    @Description("获取/查询当前小组下的所有任务清单以及清单里的所有具体子任务项，包含清单截止时间、描述、进度百分比、各个子任务的负责人、状态和子任务截止时间等详细信息")
    public BiFunction<TeamIdToolRequest, ToolContext, TeamTaskListVO> queryTeamTaskLists(
        ITaskListService taskListService,
        ITeamService teamService,
        AgentRunService agentRunService
    ) {
        return (request, toolContext) -> {
            recordReadTool(agentRunService, toolContext, "queryTeamTaskLists", "正在查询任务清单与进度");
            Long userId = getLongValue(toolContext, TOOL_CTX_USER_ID);
            if (userId == null) {
                throw new BizException(401, "当前用户未登录，无法调用工具查询任务清单");
            }

            Long requestTeamId = request == null ? null : request.getTeamId();
            Long teamId = resolveTeamIdFromContextFirst(toolContext, requestTeamId, "AI tool");
            if (teamId == null) {
                throw new BizException(400, "查询任务清单失败：小组ID不能为空");
            }

            log.info("AI calling tool queryTeamTaskLists, userId={}, teamId={}, requestTeamId={}, toolContextKeys={}",
                userId,
                teamId,
                request == null ? null : request.getTeamId(),
                toolContext == null ? null : toolContext.getContext().keySet());
            try {
                teamService.validateTeamAccessible(userId, teamId);
            } catch (BizException e) {
                if (isInvalidTeamContext(e)) {
                    log.warn("AI tool queryTeamTaskLists skipped due to invalid team context, userId={}, teamId={}, message={}",
                        userId, teamId, e.getMessage());
                    return buildInvalidTeamTaskLists();
                }
                throw e;
            }
            try {
                return taskListService.listTeamTaskLists(userId, teamId);
            } catch (Exception e) {
                log.error("Error executing queryTeamTaskLists for AI, userId={}, teamId={}", userId, teamId, e);
                throw e;
            }
        };
    }

    @Bean
    @Description("获取当前小组文档列表，可按文档类型筛选：1 表示资料文档，2 表示协作文档；不传类型时返回两类文档")
    public BiFunction<AiTeamDocumentsToolRequest, ToolContext, Map<String, Object>> queryTeamDocuments(
        IDocumentService documentService,
        ITeamService teamService,
        MongoTemplate mongoTemplate,
        AgentRunService agentRunService
    ) {
        return (request, toolContext) -> {
            recordReadTool(agentRunService, toolContext, "queryTeamDocuments", "正在查询相关文档与摘要");
            ToolIdentity identity = resolveIdentity(request, toolContext, "查询小组文档失败");
            try {
                teamService.validateTeamAccessible(identity.userId(), identity.teamId());
            } catch (BizException e) {
                if (isInvalidTeamContext(e)) {
                    log.warn("AI tool queryTeamDocuments skipped due to invalid team context, userId={}, teamId={}, message={}",
                        identity.userId(), identity.teamId(), e.getMessage());
                    return buildInvalidTeamDocuments(request == null ? null : request.getType(), identity.teamId(), e.getMessage());
                }
                throw e;
            }
            Integer type = request == null ? null : request.getType();
            Map<String, Object> result = new HashMap<>();
            if (type == null) {
                DocumentListVO resourceDocs = documentService.listTeamDocuments(identity.userId(), identity.teamId(), DOCUMENT_TYPE_RESOURCE);
                DocumentListVO collabDocs = documentService.listTeamDocuments(identity.userId(), identity.teamId(), DOCUMENT_TYPE_COLLAB);
                List<DocumentItemVO> allDocuments = new ArrayList<>();
                allDocuments.addAll(resourceDocs.getDocuments());
                allDocuments.addAll(collabDocs.getDocuments());
                Map<Long, String> aiSummaryMap = loadAiSummaryMap(allDocuments, mongoTemplate);
                result.put("resourceDocuments", sanitizeDocuments(resourceDocs.getDocuments(), aiSummaryMap));
                result.put("collaborationDocuments", sanitizeDocuments(collabDocs.getDocuments(), aiSummaryMap));
                result.put("currentUserCanUploadResource", resourceDocs.getCurrentUserCanUpload());
                result.put("currentUserCanUploadCollaboration", collabDocs.getCurrentUserCanUpload());
                return result;
            }
            DocumentListVO documents = documentService.listTeamDocuments(identity.userId(), identity.teamId(), type);
            result.put("documents", sanitizeDocuments(documents.getDocuments(), loadAiSummaryMap(documents.getDocuments(), mongoTemplate)));
            result.put("currentUserCanUpload", documents.getCurrentUserCanUpload());
            return result;
        };
    }

    @Bean
    @Description("根据 queryTeamDocuments 返回的 documentId 获取一份文档的完整正文。仅在用户问题确实需要阅读原文时调用，禁止猜测 documentId。支持资料文档和协作文档；服务端会校验当前小组访问权限。")
    public BiFunction<AiDocumentFullTextToolRequest, ToolContext, Map<String, Object>> queryDocumentFullText(
        DocumentMapper documentMapper,
        ITeamService teamService,
        MongoTemplate mongoTemplate,
        AgentRunService agentRunService
    ) {
        return (request, toolContext) -> {
            recordReadTool(agentRunService, toolContext, "queryDocumentFullText", "正在读取引用文档全文");
            Long userId = getLongValue(toolContext, TOOL_CTX_USER_ID);
            Long teamId = getLongValue(toolContext, TOOL_CTX_TEAM_ID);
            if (userId == null) {
                throw new BizException(401, "当前用户未登录，无法读取文档全文");
            }
            if (teamId == null) {
                throw new BizException(400, "读取文档全文失败：小组ID不能为空");
            }
            if (request == null || request.getDocumentId() == null || request.getDocumentId() <= 0) {
                throw new BizException(400, "读取文档全文失败：documentId不能为空");
            }
            teamService.validateTeamAccessible(userId, teamId);

            Document document = documentMapper.selectById(request.getDocumentId());
            if (document == null || !teamId.equals(document.getTeamId())) {
                throw new BizException(404, "文档不存在或不属于当前小组");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("documentId", document.getId());
            result.put("title", document.getTitle());
            result.put("type", document.getType());
            result.put("typeName", Integer.valueOf(DOCUMENT_TYPE_RESOURCE).equals(document.getType()) ? "资料文档" : "协作文档");
            result.put("fileType", document.getFileType());

            String fullText;
            if (Integer.valueOf(DOCUMENT_TYPE_RESOURCE).equals(document.getType())) {
                DocumentContentDoc content = mongoTemplate.findOne(
                    Query.query(Criteria.where("documentId").is(document.getId())),
                    DocumentContentDoc.class
                );
                fullText = content == null ? "" : normalizeToolText(content.getExtractedText());
                result.put("contentStatus", content == null ? "PENDING" : content.getParseStatus());
                result.put("contentError", content == null ? "文档正文尚未提取" : content.getParseError());
            } else if (Integer.valueOf(DOCUMENT_TYPE_COLLAB).equals(document.getType())) {
                org.bson.Document content = mongoTemplate.findOne(
                    Query.query(Criteria.where("docId").is(String.valueOf(document.getId()))),
                    org.bson.Document.class,
                    "collaboration_documents"
                );
                fullText = content == null ? "" : normalizeToolText(content.getString("plain_text"));
                result.put("contentStatus", fullText.isBlank() ? "PENDING" : "SUCCESS");
                result.put("contentError", fullText.isBlank() ? "协作文档尚未保存正文" : null);
            } else {
                throw new BizException(400, "不支持的文档类型");
            }
            result.put("textLength", fullText.length());
            result.put("fullText", fullText);
            return result;
        };
    }

    @Bean
    @Description("生成一份给当前小组指定成员发送邮件的待确认提案。recipientUserId 必须严格使用本轮 queryTeamMembers 工具结果中的 userId，禁止猜测、根据昵称或邮箱转换、或使用历史记忆中的ID。若返回 recipientValid=false，必须先调用 queryTeamMembers 后再重试。此工具绝不会发送邮件；用户将在界面中编辑并确认后由传统后端API执行。")
    public BiFunction<AiEmailProposalToolRequest, ToolContext, Map<String, Object>> proposeTeamEmail(
        AgentEmailProposalService proposalService,
        AgentRunService agentRunService
    ) {
        return (request, toolContext) -> {
            Long runId = getLongValue(toolContext, TOOL_CTX_AGENT_RUN_ID);
            Long userId = getLongValue(toolContext, TOOL_CTX_USER_ID);
            Long teamId = getLongValue(toolContext, TOOL_CTX_TEAM_ID);
            if (runId == null || userId == null || teamId == null) throw new BizException(400, "邮件提案缺少受控运行上下文");
            recordReadTool(agentRunService, toolContext, "proposeTeamEmail", "正在生成可编辑的邮件发送提案");
            try {
                var proposal = proposalService.create(runId, userId, teamId, request);
                return Map.of("proposalCreated", true, "recipientValid", true, "draftId", proposal.getDraftId(), "status", proposal.getStatus(),
                    "message", "邮件草案已生成，等待用户编辑并确认发送");
            } catch (BizException exception) {
                if (isInvalidEmailRecipient(exception)) {
                    return Map.of(
                        "proposalCreated", false,
                        "recipientValid", false,
                        "message", "收件人不是当前小组成员。请先调用 queryTeamMembers，使用其本轮返回的精确 userId 后再生成邮件提案。"
                    );
                }
                throw exception;
            }
        };
    }

    @Bean
    @Description("获取当前小组在协作过程中沉淀的工作画像，包括成员工作偏好、协作约定、长期规范、重复风险、复盘洞察和待协商议题；不返回小组资料、任务、进度或截止时间")
    public BiFunction<TeamIdToolRequest, ToolContext, Map<String, Object>> queryTeamWorkProfile(
        ITeamService teamService,
        TeamWorkProfileService teamWorkProfileService,
        AgentRunService agentRunService
    ) {
        return (request, toolContext) -> {
            recordReadTool(agentRunService, toolContext, "queryTeamWorkProfile", "正在查询团队协作画像");
            ToolIdentity identity = resolveIdentity(request, toolContext, "查询团队工作画像失败");
            try {
                teamService.validateTeamAccessible(identity.userId(), identity.teamId());
            } catch (BizException e) {
                if (isInvalidTeamContext(e)) {
                    log.warn("AI tool queryTeamWorkProfile skipped due to invalid team context, userId={}, teamId={}, message={}",
                        identity.userId(), identity.teamId(), e.getMessage());
                    Map<String, Object> result = new HashMap<>();
                    result.put("teamContextValid", false);
                    result.put("message", "当前小组上下文已失效：" + e.getMessage());
                    result.put("teamId", identity.teamId());
                    return result;
                }
                throw e;
            }
            Map<String, Object> result = teamWorkProfileService.getAgentView(identity.teamId());
            result.put("teamContextValid", true);
            return result;
        };
    }

    private ToolIdentity resolveIdentity(TeamIdToolRequest request, ToolContext toolContext, String errorPrefix) {
        Long userId = getLongValue(toolContext, TOOL_CTX_USER_ID);
        if (userId == null) {
            throw new BizException(401, "当前用户未登录，无法调用工具");
        }
        Long requestTeamId = request == null ? null : request.getTeamId();
            Long teamId = resolveTeamIdFromContextFirst(toolContext, requestTeamId, "AI tool");
        if (teamId == null) {
            throw new BizException(400, errorPrefix + "：小组ID不能为空");
        }
        return new ToolIdentity(userId, teamId);
    }

    private ToolIdentity resolveIdentity(AiTeamDocumentsToolRequest request, ToolContext toolContext, String errorPrefix) {
        Long userId = getLongValue(toolContext, TOOL_CTX_USER_ID);
        if (userId == null) {
            throw new BizException(401, "当前用户未登录，无法调用工具");
        }
        Long requestTeamId = request == null ? null : request.getTeamId();
            Long teamId = resolveTeamIdFromContextFirst(toolContext, requestTeamId, "AI tool");
        if (teamId == null) {
            throw new BizException(400, errorPrefix + "：小组ID不能为空");
        }
        return new ToolIdentity(userId, teamId);
    }

    private TeamDetailRequest buildTeamDetailRequest(Long teamId) {
        TeamDetailRequest request = new TeamDetailRequest();
        request.setTeamId(teamId);
        return request;
    }

    private int countTasks(TeamTaskListVO taskLists) {
        if (taskLists == null || taskLists.getTaskLists() == null) {
            return 0;
        }
        return taskLists.getTaskLists().stream()
            .map(TeamTaskListItemVO::getTasks)
            .mapToInt(tasks -> tasks == null ? 0 : tasks.size())
            .sum();
    }

    private int countUnfinishedTasks(TeamTaskListVO taskLists) {
        if (taskLists == null || taskLists.getTaskLists() == null) {
            return 0;
        }
        return taskLists.getTaskLists().stream()
            .map(TeamTaskListItemVO::getTasks)
            .filter(tasks -> tasks != null)
            .flatMap(List::stream)
            .map(TeamTaskItemVO::getStatus)
            .mapToInt(status -> status != null && status == 1 ? 0 : 1)
            .sum();
    }

    private List<Map<String, Object>> sanitizeMembers(List<TeamMemberManageItemVO> members) {
        if (members == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(members.size());
        for (TeamMemberManageItemVO member : members) {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", member.getUserId());
            item.put("username", member.getUsername());
            item.put("roleCode", member.getRoleCode());
            item.put("roleName", member.getRoleName());
            item.put("roleDesc", member.getRoleDesc());
            item.put("joinTime", member.getJoinTime());
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> sanitizeDocuments(List<DocumentItemVO> documents, Map<Long, String> aiSummaryMap) {
        if (documents == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(documents.size());
        for (DocumentItemVO document : documents) {
            Map<String, Object> item = new HashMap<>();
            item.put("documentId", document.getDocumentId());
            item.put("title", document.getTitle());
            item.put("type", document.getType());
            item.put("typeName", document.getTypeName());
            item.put("fileType", document.getFileType());
            item.put("fileSize", document.getFileSize());
            item.put("creatorId", document.getCreatorId());
            item.put("creatorName", document.getCreatorName());
            item.put("createTime", document.getCreateTime());
            item.put("aiSummary", aiSummaryMap.get(document.getDocumentId()));
            result.add(item);
        }
        return result;
    }

    /**
     * 资料文档与协作文档的摘要分别位于不同的 MongoDB 集合；按类型批量查询，
     * 避免 AI 工具在遍历文档列表时产生 N+1 次 MongoDB 查询。
     */
    private Map<Long, String> loadAiSummaryMap(List<DocumentItemVO> documents, MongoTemplate mongoTemplate) {
        if (documents == null || documents.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> summaryMap = new HashMap<>();
        List<Long> resourceDocumentIds = documents.stream()
            .filter(document -> Integer.valueOf(DOCUMENT_TYPE_RESOURCE).equals(document.getType()))
            .map(DocumentItemVO::getDocumentId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        if (!resourceDocumentIds.isEmpty()) {
            List<DocumentContentDoc> contents = mongoTemplate.find(
                Query.query(Criteria.where("documentId").in(resourceDocumentIds)),
                DocumentContentDoc.class
            );
            for (DocumentContentDoc content : contents) {
                summaryMap.put(content.getDocumentId(), content.getAiSummary());
            }
        }

        Set<String> collaborationDocumentIds = documents.stream()
            .filter(document -> Integer.valueOf(DOCUMENT_TYPE_COLLAB).equals(document.getType()))
            .map(DocumentItemVO::getDocumentId)
            .filter(java.util.Objects::nonNull)
            .map(String::valueOf)
            .collect(Collectors.toSet());
        if (!collaborationDocumentIds.isEmpty()) {
            List<org.bson.Document> contents = mongoTemplate.find(
                Query.query(Criteria.where("docId").in(collaborationDocumentIds)),
                org.bson.Document.class,
                "collaboration_documents"
            );
            for (org.bson.Document content : contents) {
                String documentId = content.getString("docId");
                String summary = content.getString("ai_summary");
                if (documentId != null) {
                    try {
                        summaryMap.put(Long.valueOf(documentId), summary);
                    } catch (NumberFormatException e) {
                        log.warn("Ignoring collaboration document summary with invalid documentId={}", documentId);
                    }
                }
            }
        }
        return summaryMap;
    }

    private Long resolveTeamIdFromContextFirst(ToolContext toolContext, Long requestTeamId, String toolName) {
        Long contextTeamId = getLongValue(toolContext, TOOL_CTX_TEAM_ID);
        if (contextTeamId != null) {
            if (requestTeamId != null && !requestTeamId.equals(contextTeamId)) {
                log.warn("AI tool request teamId ignored, toolName={}, requestTeamId={}, contextTeamId={}",
                    toolName, requestTeamId, contextTeamId);
            }
            return contextTeamId;
        }
        return requestTeamId;
    }

    private String normalizeToolText(String text) {
        return text == null ? "" : text.trim();
    }

    private Long getLongValue(ToolContext toolContext, String key) {
        if (toolContext == null) {
            return null;
        }
        Map<String, Object> context = toolContext.getContext();
        Object value = context == null ? null : context.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid tool context value for key={}, value={}", key, str);
            }
        }
        return null;
    }

    private boolean isInvalidTeamContext(BizException e) {
        if (e == null || e.getCode() == null) {
            return false;
        }
        return e.getCode() == 403 || e.getCode() == 404;
    }

    private boolean isInvalidEmailRecipient(BizException exception) {
        return exception != null
            && exception.getCode() != null
            && exception.getCode() == 403
            && "目标用户不是当前小组成员".equals(exception.getMessage());
    }

    private Map<String, Object> buildInvalidTeamOverview(Long teamId, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("teamContextValid", false);
        result.put("message", "当前小组上下文已失效：" + message);
        result.put("team", Map.of("teamId", teamId == null ? "" : String.valueOf(teamId)));
        result.put("currentUserRole", Map.of("roleName", "", "roleDesc", ""));
        result.put("memberCount", 0);
        result.put("taskListCount", 0);
        result.put("taskCount", 0);
        result.put("unfinishedTaskCount", 0);
        result.put("resourceDocumentCount", 0);
        result.put("collaborationDocumentCount", 0);
        return result;
    }

    private Map<String, Object> buildInvalidTeamMembers(Long teamId, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("teamContextValid", false);
        result.put("teamId", teamId);
        result.put("message", "当前小组上下文已失效：" + message);
        result.put("currentUserRoleName", "");
        result.put("currentUserRoleDesc", "");
        result.put("currentUserCaptain", false);
        result.put("members", List.of());
        return result;
    }

    private TeamTaskListVO buildInvalidTeamTaskLists() {
        return TeamTaskListVO.builder()
            .currentUserCanCreate(false)
            .taskLists(List.of())
            .build();
    }

    private Map<String, Object> buildInvalidTeamDocuments(Integer type, Long teamId, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("teamContextValid", false);
        result.put("teamId", teamId);
        result.put("message", "当前小组上下文已失效：" + message);
        if (type == null) {
            result.put("resourceDocuments", List.of());
            result.put("collaborationDocuments", List.of());
            result.put("currentUserCanUploadResource", false);
            result.put("currentUserCanUploadCollaboration", false);
            return result;
        }
        result.put("documents", List.of());
        result.put("currentUserCanUpload", false);
        return result;
    }

    private record ToolIdentity(Long userId, Long teamId) {
    }
}
