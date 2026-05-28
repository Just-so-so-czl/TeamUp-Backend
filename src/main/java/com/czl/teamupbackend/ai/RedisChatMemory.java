package com.czl.teamupbackend.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@RequiredArgsConstructor
public class RedisChatMemory implements ChatMemory {

    private static final String KEY_PREFIX = "chat:memory:";
    private static final Duration TTL = Duration.ofHours(12);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<MessageSnapshot> history = loadSnapshots(conversationId);
        for (Message message : messages) {
            history.add(MessageSnapshot.of(message));
        }
        saveSnapshots(conversationId, history);
    }

    @Override
    public List<Message> get(String conversationId) {
        List<MessageSnapshot> snapshots = loadSnapshots(conversationId);
        List<Message> messages = new ArrayList<>(snapshots.size());
        for (MessageSnapshot snapshot : snapshots) {
            messages.add(snapshot.toMessage());
        }
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(key(conversationId));
    }

    private List<MessageSnapshot> loadSnapshots(String conversationId) {
        String raw = redisTemplate.opsForValue().get(key(conversationId));
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<MessageSnapshot>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse chat memory, conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    private void saveSnapshots(String conversationId, List<MessageSnapshot> snapshots) {
        try {
            String raw = objectMapper.writeValueAsString(snapshots);
            redisTemplate.opsForValue().set(key(conversationId), raw, TTL);
        } catch (Exception e) {
            log.warn("Failed to save chat memory, conversationId={}", conversationId, e);
        }
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    @Data
    @AllArgsConstructor
    private static class MessageSnapshot {
        private String type;
        private String text;

        static MessageSnapshot of(Message message) {
            return new MessageSnapshot(message.getMessageType().name(), message.getText());
        }

        Message toMessage() {
            if ("ASSISTANT".equalsIgnoreCase(type)) {
                return new AssistantMessage(text);
            }
            if ("SYSTEM".equalsIgnoreCase(type)) {
                return new SystemMessage(text);
            }
            return new UserMessage(text);
        }
    }
}

