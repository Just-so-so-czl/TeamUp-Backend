package com.czl.teamupbackend.model.mongo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "chat_memory_docs")
@CompoundIndexes({
    @CompoundIndex(name = "idx_session_type", def = "{'sessionId': 1, 'memoryType': 1}", unique = true)
})
public class MentorMemoryDoc {

    @Id
    private String id;

    private Long sessionId;

    private Long teamId;

    /**
     * MID / EARLY
     */
    private String memoryType;

    private Integer tokenCount;

    private List<MemorySegment> segments = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemorySegment {
        private String content;
        private Integer tokenCount;
        private LocalDateTime createdAt;
    }
}
