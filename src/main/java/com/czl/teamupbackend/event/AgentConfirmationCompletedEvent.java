package com.czl.teamupbackend.event;

/** A confirmed controlled action that should resume the same Agent run. */
public record AgentConfirmationCompletedEvent(
    Long runId,
    Long userId,
    String toolName,
    String resultSummary
) {
}
