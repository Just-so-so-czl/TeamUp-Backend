package com.czl.teamupbackend.event;

public record CollaborationDocumentSummaryRequestedEvent(
    Long documentId,
    String title,
    String sourceText,
    String sourceTextHash,
    int sourceTextLength,
    String triggerType
) {
}
