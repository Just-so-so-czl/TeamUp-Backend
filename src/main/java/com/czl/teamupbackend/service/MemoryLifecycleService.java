package com.czl.teamupbackend.service;

import com.czl.teamupbackend.event.MemoryCompressionEvent;
import com.czl.teamupbackend.model.entity.AiChatMessageIndex;

public interface MemoryLifecycleService {

    void onShortTermMessageAdded(AiChatMessageIndex messageIndex);

    void triggerMidTermCompression(Long sessionId);

    void triggerEarlyTermCompression(Long sessionId);

    void processMidTermCompression(MemoryCompressionEvent event);

    void processEarlyTermCompression(MemoryCompressionEvent event);
}
