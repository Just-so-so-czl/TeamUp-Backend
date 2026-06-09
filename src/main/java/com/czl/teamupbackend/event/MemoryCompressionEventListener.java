package com.czl.teamupbackend.event;

import com.czl.teamupbackend.service.MemoryLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryCompressionEventListener {

    private final MemoryLifecycleService memoryLifecycleService;

    @Async("memoryLifecycleExecutor")
    @EventListener(condition = "#event.eventType.name() == 'MID_TERM_CHECK'")
    public void onMidTermCheck(MemoryCompressionEvent event) {
        memoryLifecycleService.processMidTermCompression(event);
    }

    @Async("memoryLifecycleExecutor")
    @EventListener(condition = "#event.eventType.name() == 'EARLY_TERM_CHECK'")
    public void onEarlyTermCheck(MemoryCompressionEvent event) {
        memoryLifecycleService.processEarlyTermCompression(event);
    }
}
