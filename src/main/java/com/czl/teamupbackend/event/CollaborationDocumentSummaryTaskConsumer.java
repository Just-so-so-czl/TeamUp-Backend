package com.czl.teamupbackend.event;

import com.czl.teamupbackend.mq.DocumentTextExtractConsumer;
import com.czl.teamupbackend.service.CollaborationDocumentSummaryService;
import com.czl.teamupbackend.service.TeamWorkProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationDocumentSummaryTaskConsumer {

    private final DocumentTextExtractConsumer documentTextExtractConsumer;
    private final CollaborationDocumentSummaryService collaborationDocumentSummaryService;
    private final TeamWorkProfileService teamWorkProfileService;

    @Async
    @EventListener
    public void handle(CollaborationDocumentSummaryRequestedEvent event) {
        try {
            String summary = documentTextExtractConsumer.generateDocumentSummary(event.title(), event.sourceText());
            if (!StringUtils.hasText(summary)) {
                throw new IllegalStateException("摘要模型返回为空");
            }
            boolean stored = collaborationDocumentSummaryService.completeSummary(event, summary);
            if (stored) {
                teamWorkProfileService.requestExtraction(
                    event.teamId(),
                    "COLLABORATION_DOCUMENT",
                    String.valueOf(event.documentId()),
                    event.title(),
                    null,
                    event.sourceText()
                );
            }
        } catch (Exception e) {
            collaborationDocumentSummaryService.failSummary(event, e);
            log.error("Collaboration summary generation failed, documentId={}, triggerType={}",
                event.documentId(), event.triggerType(), e);
        } finally {
            collaborationDocumentSummaryService.releaseSummaryLock(event.documentId());
        }
    }
}
