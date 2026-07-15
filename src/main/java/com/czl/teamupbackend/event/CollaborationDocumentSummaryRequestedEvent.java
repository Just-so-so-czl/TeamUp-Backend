package com.czl.teamupbackend.event;

public record CollaborationDocumentSummaryRequestedEvent(
    Long documentId,
    Long teamId,
    String title,
    String sourceText,
    String sourceTextHash,
    int sourceTextLength,
    String triggerType
) {
}
