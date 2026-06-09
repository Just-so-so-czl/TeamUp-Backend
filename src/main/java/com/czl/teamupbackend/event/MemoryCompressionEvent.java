package com.czl.teamupbackend.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryCompressionEvent {

    private Long sessionId;

    private MemoryCompressionEventType eventType;

    private Integer retryCount;
}
