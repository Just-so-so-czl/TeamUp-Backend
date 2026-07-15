package com.czl.teamupbackend.event;

public record TeamWorkProfileExtractionRequestedEvent(
    Long teamId,
    String sourceType,
    String sourceId,
    String sourceTitle,
    Long sourceUserId,
    String sourceContent,
    String sourceHash
) {
}
