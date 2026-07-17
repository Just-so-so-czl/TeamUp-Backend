package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.czl.teamupbackend.mapper.AiAgentRunMapper;
import com.czl.teamupbackend.mapper.AiAgentStepMapper;
import com.czl.teamupbackend.model.entity.AiAgentRun;
import com.czl.teamupbackend.model.entity.AiAgentStep;
import com.czl.teamupbackend.service.AgentRunService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunServiceImpl implements AgentRunService {
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_WAITING_CONFIRMATION = "WAITING_CONFIRMATION";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_STEPS = 20;
    private final AiAgentRunMapper agentRunMapper;
    private final AiAgentStepMapper agentStepMapper;
    private final TransactionTemplate transactionTemplate;
    private final Map<Long, Consumer<AgentRunProgress>> listeners = new ConcurrentHashMap<>();

    @Override
    public AiAgentRun start(Long sessionId, Long teamId, Long userId, String traceId, String sceneType, String goal, int promptTokens) {
        LocalDateTime now = LocalDateTime.now();
        String planJson = buildInitialPlan(goal);
        AiAgentRun run = new AiAgentRun().setSessionId(sessionId).setTeamId(teamId).setUserId(userId)
            .setTraceId(traceId).setSceneType(sceneType).setGoal(limit(goal, 500))
            .setPlanJson(planJson)
            .setPlanVersion(1).setStatus(STATUS_RUNNING).setStepCount(0).setPromptTokens(promptTokens)
            .setCompletionTokens(0).setErrorMsg("").setStartedAt(now);
        agentRunMapper.insert(run);
        return run;
    }

    @Override
    public void registerListener(Long runId, Consumer<AgentRunProgress> listener) {
        if (runId != null && listener != null) listeners.put(runId, listener);
    }

    @Override
    public void unregisterListener(Long runId) {
        if (runId != null) listeners.remove(runId);
    }

    @Override
    public void markPlanning(Long runId) {
        recordStep(runId, "ANALYZE", null, "正在理解目标并制定最短执行路径", "DONE");
    }

    @Override
    public void recordReadTool(Long runId, String toolName, String summary) {
        recordStep(runId, "READ", toolName, summary, "DONE");
    }

    @Override
    public void awaitConfirmation(Long runId, String summary) {
        awaitConfirmation(runId, "proposeTeamEmail", summary);
    }

    @Override
    public void awaitConfirmation(Long runId, String toolName, String summary) {
        if (runId == null) return;
        String safeSummary = limit(summary == null ? "等待用户确认后执行" : summary, 500);
        agentRunMapper.updateById(new AiAgentRun().setId(runId).setStatus(STATUS_WAITING_CONFIRMATION));
        recordStep(runId, "DRAFT", toolName, safeSummary, STATUS_WAITING_CONFIRMATION);
    }

    @Override
    public boolean isWaitingConfirmation(Long runId) {
        if (runId == null) {
            return false;
        }
        AiAgentRun run = agentRunMapper.selectById(runId);
        return run != null && STATUS_WAITING_CONFIRMATION.equals(run.getStatus());
    }

    @Override
    public void resumeAfterConfirmedWrite(Long runId, String resultSummary) {
        resumeAfterConfirmedWrite(runId, "sendTeamEmail", resultSummary);
    }

    @Override
    public void resumeAfterConfirmedWrite(Long runId, String toolName, String resultSummary) {
        if (runId == null) return;
        String safeSummary = limit(resultSummary == null ? "已按确认内容执行" : resultSummary, 500);
        agentRunMapper.updateById(new AiAgentRun().setId(runId).setStatus(STATUS_RUNNING));
        recordStep(runId, "WRITE", toolName, safeSummary, "DONE");
        recordStep(runId, "VERIFY", toolName, "用户确认操作结果已验证", "DONE");
    }

    @Override
    public void markAnswering(Long runId) {
        recordStep(runId, "ANSWER", null, "正在整合结果并生成答复", "RUNNING");
    }

    @Override
    public void complete(Long runId, Integer completionTokens) {
        if (runId == null) return;
        AiAgentRun run = agentRunMapper.selectById(runId);
        if (run != null && STATUS_WAITING_CONFIRMATION.equals(run.getStatus())) return;
        agentRunMapper.updateById(new AiAgentRun().setId(runId).setStatus(STATUS_COMPLETED)
            .setCompletionTokens(completionTokens == null ? 0 : completionTokens)
            .setFinishedAt(LocalDateTime.now()).setErrorMsg(""));
        publish(runId, STATUS_COMPLETED, "FINISH", null, "已完成");
    }

    @Override
    public void fail(Long runId, String errorMessage) {
        if (runId == null) return;
        String safeMessage = limit(errorMessage == null ? "Agent 执行失败" : errorMessage, 500);
        agentRunMapper.updateById(new AiAgentRun().setId(runId).setStatus(STATUS_FAILED)
            .setErrorMsg(safeMessage).setFinishedAt(LocalDateTime.now()));
        publish(runId, STATUS_FAILED, "FINISH", null, safeMessage);
    }

    private void recordStep(Long runId, String stepType, String toolName, String summary, String status) {
        if (runId == null) {
            return;
        }
        Boolean recorded = transactionTemplate.execute(statusHolder -> recordStepInTransaction(runId, stepType, toolName, summary, status));
        if (Boolean.TRUE.equals(recorded)) {
            publish(runId, status, stepType, toolName, summary);
        }
    }

    private boolean recordStepInTransaction(Long runId, String stepType, String toolName, String summary, String status) {
        // Tool callbacks and streamed response callbacks run concurrently. The row lock serializes step allocation.
        AiAgentRun lockedRun = agentRunMapper.selectByIdForUpdate(runId);
        if (lockedRun == null) {
            log.warn("Agent run does not exist while recording step, runId={}", runId);
            return false;
        }
        long count = agentStepMapper.selectCount(new LambdaQueryWrapper<AiAgentStep>().eq(AiAgentStep::getRunId, runId));
        if (count >= MAX_STEPS) {
            log.warn("Agent step limit reached, runId={}", runId);
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        int stepNo = (int) count + 1;
        agentStepMapper.insert(new AiAgentStep().setRunId(runId).setStepNo(stepNo)
            .setStepType(stepType).setToolName(toolName).setStatus(status).setDecisionSummary(limit(summary, 500))
            .setObservationSummary("").setDurationMs(0).setPromptTokens(0).setCompletionTokens(0)
            .setStartedAt(now).setFinishedAt("RUNNING".equals(status) ? null : now));
        agentRunMapper.updateById(new AiAgentRun().setId(runId).setStepCount(stepNo));
        return true;
    }

    private void publish(Long runId, String status, String stepType, String toolName, String summary) {
        Consumer<AgentRunProgress> listener = listeners.get(runId);
        if (listener == null) return;
        try {
            listener.accept(new AgentRunProgress(String.valueOf(runId), status, stepType, toolName, summary));
        } catch (Exception e) {
            log.debug("Publish agent progress failed, runId={}", runId, e);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String buildInitialPlan(String goal) {
        String normalized = goal == null ? "" : goal;
        boolean needsWrite = normalized.matches("(?s).*?(创建|新建|分配|指派|修改|删除|提交|发布).*?");
        boolean needsRealtimeData = normalized.matches("(?s).*?(任务|成员|文档|截止|进度|小组|协作).*?");
        String steps;
        if (needsWrite) {
            steps = "[{\"stepId\":\"step_1\",\"type\":\"ANALYZE\",\"status\":\"READY\"},"
                + "{\"stepId\":\"step_2\",\"type\":\"DRAFT\",\"status\":\"READY\"},"
                + "{\"stepId\":\"step_3\",\"type\":\"WRITE\",\"requiresConfirmation\":true,\"status\":\"WAITING_CONFIRMATION\"}]";
        } else if (needsRealtimeData) {
            steps = "[{\"stepId\":\"step_1\",\"type\":\"READ\",\"status\":\"READY\"},"
                + "{\"stepId\":\"step_2\",\"type\":\"ANSWER\",\"status\":\"READY\"}]";
        } else {
            steps = "[{\"stepId\":\"step_1\",\"type\":\"ANSWER\",\"status\":\"READY\"}]";
        }
        return "{\"mode\":\"ADAPTIVE_PLAN_REACT\",\"planVersion\":1,\"steps\":" + steps + "}";
    }
}
