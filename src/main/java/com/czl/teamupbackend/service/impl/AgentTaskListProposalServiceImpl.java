package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.mapper.AiActionDraftMapper;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.model.dto.AiTaskListProposalToolRequest;
import com.czl.teamupbackend.model.entity.AiActionDraft;
import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.model.vo.AgentTaskListProposalVO;
import com.czl.teamupbackend.service.AgentRunService;
import com.czl.teamupbackend.service.AgentTaskListProposalService;
import com.czl.teamupbackend.service.ITaskListService;
import com.czl.teamupbackend.service.ITaskService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentTaskListProposalServiceImpl implements AgentTaskListProposalService {
    private static final String ACTION_TYPE = "TASK_LIST_CREATE";
    private static final String PENDING = "PENDING_CONFIRMATION";
    private static final String EXECUTED = "EXECUTED";
    private final AiActionDraftMapper draftMapper;
    private final TeamMapper teamMapper;
    private final ObjectMapper objectMapper;
    private final ITaskListService taskListService;
    private final ITaskService taskService;
    private final AgentRunService agentRunService;

    @Override
    public AgentTaskListProposalVO create(Long runId, Long userId, Long teamId, AiTaskListProposalToolRequest request) {
        if (runId == null || userId == null || teamId == null || request == null) throw new BizException(400, "任务清单提案参数不完整");
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getTotalDeadline() == null) throw new BizException(409, "小组未设置总截止时间，无法生成任务清单草案");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title(request.getTitle())); payload.put("description", description(request.getDescription()));
        payload.put("deadline", team.getTotalDeadline().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        payload.put("taskDescriptions", tasks(request.getTaskDescriptions()));
        AiActionDraft draft = new AiActionDraft().setRunId(runId).setTeamId(teamId).setCreatorUserId(userId)
            .setActionType(ACTION_TYPE).setStatus(PENDING).setPayloadJson(write(payload)).setResultSummary("").setErrorMsg("").setCreatedAt(LocalDateTime.now());
        draftMapper.insert(draft);
        agentRunService.awaitConfirmation(runId, "proposeTaskList", "已生成任务清单草案，等待用户编辑并确认创建");
        return toVo(draft, payload);
    }

    @Override public AgentTaskListProposalVO getPending(Long userId, Long runId) { AiActionDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<AiActionDraft>().eq(AiActionDraft::getRunId, runId).eq(AiActionDraft::getCreatorUserId, userId).eq(AiActionDraft::getActionType, ACTION_TYPE).last("LIMIT 1")); return draft == null ? null : toVo(draft, read(draft)); }

    @Override @Transactional(rollbackFor = Exception.class)
    public AgentTaskListProposalVO execute(Long userId, Long draftId, String title, String description, String deadline, List<String> taskDescriptions) {
        AiActionDraft draft = draftMapper.selectById(draftId);
        if (draft == null || !userId.equals(draft.getCreatorUserId()) || !ACTION_TYPE.equals(draft.getActionType())) throw new BizException(404, "任务清单提案不存在或无权限");
        if (EXECUTED.equals(draft.getStatus())) return toVo(draft, read(draft));
        if (draftMapper.update(null, new LambdaUpdateWrapper<AiActionDraft>().eq(AiActionDraft::getId, draftId).eq(AiActionDraft::getStatus, PENDING).set(AiActionDraft::getStatus, "EXECUTING")) != 1) throw new BizException(409, "该任务清单草案正在处理或已完成");
        Map<String, Object> payload = read(draft); payload.put("title", title(title)); payload.put("description", description(description));
        LocalDateTime due = LocalDateTime.parse(deadline, DateTimeFormatter.ISO_LOCAL_DATE_TIME); payload.put("deadline", due.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)); payload.put("taskDescriptions", tasks(taskDescriptions));
        taskListService.createTaskList(userId, draft.getTeamId(), (String) payload.get("title"), (String) payload.get("description"), due);
        var latest = taskListService.list(new LambdaQueryWrapper<com.czl.teamupbackend.model.entity.TaskList>().eq(com.czl.teamupbackend.model.entity.TaskList::getTeamId, draft.getTeamId()).eq(com.czl.teamupbackend.model.entity.TaskList::getCreatorId, userId).eq(com.czl.teamupbackend.model.entity.TaskList::getTitle, payload.get("title")).orderByDesc(com.czl.teamupbackend.model.entity.TaskList::getCreateTime).last("LIMIT 1"));
        if (latest.isEmpty()) throw new BizException(500, "任务清单创建后未找到记录");
        for (String task : (List<String>) payload.get("taskDescriptions")) taskService.createTask(userId, latest.get(0).getId(), task, due);
        String summary = "已创建任务清单“" + payload.get("title") + "”及" + ((List<?>) payload.get("taskDescriptions")).size() + "个子任务";
        draftMapper.updateById(new AiActionDraft().setId(draftId).setStatus(EXECUTED).setPayloadJson(write(payload)).setResultSummary(summary).setExecutedAt(LocalDateTime.now()));
        agentRunService.resumeAfterConfirmedWrite(draft.getRunId(), "createTaskList", summary); draft.setStatus(EXECUTED); draft.setPayloadJson(write(payload)); draft.setResultSummary(summary); return toVo(draft, payload);
    }

    @Override public Map<Long, AgentTaskListProposalVO> findByRunIds(Long userId, Collection<Long> runIds) { if (runIds == null || runIds.isEmpty()) return Map.of(); return draftMapper.selectList(new LambdaQueryWrapper<AiActionDraft>().eq(AiActionDraft::getCreatorUserId, userId).eq(AiActionDraft::getActionType, ACTION_TYPE).in(AiActionDraft::getRunId, runIds)).stream().collect(Collectors.toMap(AiActionDraft::getRunId, draft -> toVo(draft, read(draft)), (n, o) -> n)); }
    private String title(String value) { value = safe(value).trim(); if (value.length() < 2 || value.length() > 150) throw new BizException(400, "任务清单名称长度需在2到150个字符之间"); return value; }
    private String description(String value) { value = safe(value).trim(); if (value.length() > 1000) throw new BizException(400, "任务清单描述不能超过1000个字符"); return value; }
    private List<String> tasks(List<String> values) { if (values == null || values.isEmpty() || values.size() > 30) throw new BizException(400, "子任务数量需在1到30之间"); List<String> result = values.stream().map(v -> safe(v).trim()).filter(v -> !v.isEmpty()).distinct().toList(); if (result.isEmpty() || result.stream().anyMatch(v -> v.length() > 500)) throw new BizException(400, "子任务描述不合法"); return result; }
    private String write(Map<String, Object> p) { try { return objectMapper.writeValueAsString(p); } catch (Exception e) { throw new BizException(500, "保存任务草案失败"); } }
    private Map<String, Object> read(AiActionDraft d) { try { return objectMapper.readValue(d.getPayloadJson(), new TypeReference<>() {}); } catch (Exception e) { throw new BizException(500, "任务草案数据损坏"); } }
    private AgentTaskListProposalVO toVo(AiActionDraft d, Map<String, Object> p) { return AgentTaskListProposalVO.builder().draftId(String.valueOf(d.getId())).runId(String.valueOf(d.getRunId())).status(d.getStatus()).title(String.valueOf(p.get("title"))).description(String.valueOf(p.get("description"))).deadline(String.valueOf(p.get("deadline"))).taskDescriptions((List<String>) p.get("taskDescriptions")).resultSummary(d.getResultSummary()).build(); }
    private String safe(String value) { return value == null ? "" : value; }
}
