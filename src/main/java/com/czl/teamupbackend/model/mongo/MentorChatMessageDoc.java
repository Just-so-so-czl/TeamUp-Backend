package com.czl.teamupbackend.model.mongo;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "chat_messages")
@CompoundIndexes({
    @CompoundIndex(name = "idx_session_created_at", def = "{'sessionId': 1, 'createdAt': 1}")
})
public class MentorChatMessageDoc {

    @Id
    private String id;

    private Long messageIndexId;

    private Long sessionId;

    private Long teamId;

    private Long userId;

    private String senderType;

    private String messageType;

    private String content;

    private Map<String, Object> structuredPayload;

    @Indexed
    private String traceId;

    private LocalDateTime createdAt;
}

