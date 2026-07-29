package com.czl.teamupbackend.event;

/** A rejected controlled action that should resume the same Agent run for its final summary only. */
public record AgentProposalRejectedEvent(
    Long runId,
    Long userId,
    String toolName,
    String resultSummary
) {
}
