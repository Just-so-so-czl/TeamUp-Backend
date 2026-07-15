package com.czl.teamupbackend.service;

import com.czl.teamupbackend.event.TeamWorkProfileExtractionRequestedEvent;
import java.util.Map;

public interface TeamWorkProfileService {

    void requestExtraction(
        Long teamId,
        String sourceType,
        String sourceId,
        String sourceTitle,
        Long sourceUserId,
        String sourceContent
    );

    void processExtraction(TeamWorkProfileExtractionRequestedEvent event);

    Map<String, Object> getAgentView(Long teamId);
}
