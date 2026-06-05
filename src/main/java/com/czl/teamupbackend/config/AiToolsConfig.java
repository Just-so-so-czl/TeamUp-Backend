package com.czl.teamupbackend.config;

import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.model.dto.AiTeamDocumentsToolRequest;
import com.czl.teamupbackend.model.dto.TeamIdToolRequest;
import com.czl.teamupbackend.model.dto.TeamDetailRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

@Slf4j
@Configuration
public class AiToolsConfig {

    private static final String TOOL_CTX_USER_ID = "userId";
    private static final String TOOL_CTX_TEAM_ID = "teamId";
    private static final int DOCUMENT_TYPE_RESOURCE = 1;
    private static final int DOCUMENT_TYPE_COLLAB = 2;

    @Bean
    @Description("获取当前小组的整体概览信息，包括小组基础信息、成员数量、任务数量、未完成任务数量、文档数量和当前用户在小组中的角色")
    public BiFunction<TeamIdToolRequest, ToolContext, Map<String, Object>> queryTeamOverview(
        ITeamService teamService,
        ITeamMemberService teamMemberService,
        ITaskListService taskListService,
        IDocumentService documentService
    ) {
        return (request, toolContext) -> {
            ToolIdentity identity = resolveIdentity(request, toolContext, "查询小组概览失败");
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
    public BiFunction<TeamIdToolRequest, ToolContext, Map<String, Object>> queryTeamMembers(ITeamMemberService teamMemberService) {
        return (request, toolContext) -> {
            ToolIdentity identity = resolveIdentity(request, toolContext, "查询小组成员失败");
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
    public BiFunction<TeamIdToolRequest, ToolContext, TeamTaskListVO> queryTeamTaskLists(ITaskListService taskListService) {
        return (request, toolContext) -> {
            Long userId = getLongValue(toolContext, TOOL_CTX_USER_ID);
            if (userId == null) {
                throw new BizException(401, "当前用户未登录，无法调用工具查询任务清单");
            }

            Long teamId = request == null ? null : request.getTeamId();
            if (teamId == null) {
                teamId = getLongValue(toolContext, TOOL_CTX_TEAM_ID);
            }
            if (teamId == null) {
                throw new BizException(400, "查询任务清单失败：小组ID不能为空");
            }

            log.info("AI calling tool queryTeamTaskLists, userId={}, teamId={}, requestTeamId={}, toolContextKeys={}",
                userId,
                teamId,
                request == null ? null : request.getTeamId(),
                toolContext == null ? null : toolContext.getContext().keySet());
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
    public BiFunction<AiTeamDocumentsToolRequest, ToolContext, Map<String, Object>> queryTeamDocuments(IDocumentService documentService) {
        return (request, toolContext) -> {
            ToolIdentity identity = resolveIdentity(request, toolContext, "查询小组文档失败");
            Integer type = request == null ? null : request.getType();
            Map<String, Object> result = new HashMap<>();
            if (type == null) {
                DocumentListVO resourceDocs = documentService.listTeamDocuments(identity.userId(), identity.teamId(), DOCUMENT_TYPE_RESOURCE);
                DocumentListVO collabDocs = documentService.listTeamDocuments(identity.userId(), identity.teamId(), DOCUMENT_TYPE_COLLAB);
                result.put("resourceDocuments", sanitizeDocuments(resourceDocs.getDocuments()));
                result.put("collaborationDocuments", sanitizeDocuments(collabDocs.getDocuments()));
                result.put("currentUserCanUploadResource", resourceDocs.getCurrentUserCanUpload());
                result.put("currentUserCanUploadCollaboration", collabDocs.getCurrentUserCanUpload());
                return result;
            }
            DocumentListVO documents = documentService.listTeamDocuments(identity.userId(), identity.teamId(), type);
            result.put("documents", sanitizeDocuments(documents.getDocuments()));
            result.put("currentUserCanUpload", documents.getCurrentUserCanUpload());
            return result;
        };
    }

    private ToolIdentity resolveIdentity(TeamIdToolRequest request, ToolContext toolContext, String errorPrefix) {
        Long userId = getLongValue(toolContext, TOOL_CTX_USER_ID);
        if (userId == null) {
            throw new BizException(401, "当前用户未登录，无法调用工具");
        }
        Long teamId = request == null ? null : request.getTeamId();
        if (teamId == null) {
            teamId = getLongValue(toolContext, TOOL_CTX_TEAM_ID);
        }
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
        Long teamId = request == null ? null : request.getTeamId();
        if (teamId == null) {
            teamId = getLongValue(toolContext, TOOL_CTX_TEAM_ID);
        }
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

    private List<Map<String, Object>> sanitizeDocuments(List<DocumentItemVO> documents) {
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
            result.add(item);
        }
        return result;
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

    private record ToolIdentity(Long userId, Long teamId) {
    }
}
