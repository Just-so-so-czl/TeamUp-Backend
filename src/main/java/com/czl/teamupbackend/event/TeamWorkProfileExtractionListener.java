package com.czl.teamupbackend.event;

import com.czl.teamupbackend.service.TeamWorkProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeamWorkProfileExtractionListener {

    private final TeamWorkProfileService teamWorkProfileService;

    @Async
    @EventListener
    public void handle(TeamWorkProfileExtractionRequestedEvent event) {
        try {
            teamWorkProfileService.processExtraction(event);
        } catch (Exception e) {
            log.error("Team work profile extraction failed, teamId={}, sourceType={}, sourceId={}",
                event.teamId(), event.sourceType(), event.sourceId(), e);
        }
    }
}
