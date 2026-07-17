package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.entity.AiAgentRun;
import java.util.function.Consumer;

/** Coordinates the durable run audit trail and transient SSE progress notifications. */
public interface AgentRunService {
    AiAgentRun start(Long sessionId, Long teamId, Long userId, String traceId, String sceneType, String goal, int promptTokens);
    void registerListener(Long runId, Consumer<AgentRunProgress> listener);
    void unregisterListener(Long runId);
    void markPlanning(Long runId);
    void recordReadTool(Long runId, String toolName, String summary);
    void awaitConfirmation(Long runId, String summary);
    void awaitConfirmation(Long runId, String toolName, String summary);
    boolean isWaitingConfirmation(Long runId);
    void resumeAfterConfirmedWrite(Long runId, String resultSummary);
    void resumeAfterConfirmedWrite(Long runId, String toolName, String resultSummary);
    void markAnswering(Long runId);
    void complete(Long runId, Integer completionTokens);
    void fail(Long runId, String errorMessage);

    record AgentRunProgress(String runId, String status, String stepType, String toolName, String summary) {
    }
}
