package com.czl.teamupbackend.event;

import com.czl.teamupbackend.service.IMentorChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentConfirmationCompletedListener {

    private final IMentorChatService mentorChatService;

    @Async("mvcAsyncTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void resume(AgentConfirmationCompletedEvent event) {
        log.info("Agent confirmation event received, runId={}, userId={}, toolName={}",
            event.runId(), event.userId(), event.toolName());
        try {
            mentorChatService.resumeAfterConfirmation(event.runId(), event.userId(), event.toolName(), event.resultSummary());
            log.info("Agent confirmation resume dispatch completed, runId={}, toolName={}", event.runId(), event.toolName());
        } catch (Exception e) {
            log.error("Resume Agent after confirmed action failed, runId={}, toolName={}", event.runId(), event.toolName(), e);
        }
    }
}
