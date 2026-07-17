package com.czl.teamupbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Small cache-aside helper for team read models. Redis failures must never prevent business reads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamRedisCacheService {

    public static final Duration TEAM_BASE_TTL = Duration.ofMinutes(5);
    public static final Duration TEAM_MEMBERS_TTL = Duration.ofMinutes(3);
    public static final Duration TASK_BOARD_TTL = Duration.ofSeconds(45);
    public static final Duration TEAM_WORK_PROFILE_TTL = Duration.ofMinutes(10);
    public static final Duration CHAT_HISTORY_TTL = Duration.ofMinutes(20);

    private static final String TEAM_BASE_PREFIX = "team:base:";
    private static final String TEAM_MEMBERS_VERSION_PREFIX = "team:members:version:";
    private static final String TEAM_MEMBERS_PREFIX = "team:members:";
    private static final String TASK_BOARD_VERSION_PREFIX = "team:task-board:version:";
    private static final String TASK_BOARD_PREFIX = "team:task-board:";
    private static final String TEAM_WORK_PROFILE_PREFIX = "team:work-profile:";
    private static final String CHAT_HISTORY_PREFIX = "chat:history-prompt:";
    private static final String CHAT_SHORT_TERM_MEMORY_PREFIX = "chat:memory:short-term:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public String teamBaseKey(Long teamId) {
        return TEAM_BASE_PREFIX + teamId;
    }

    public String teamMembersKey(Long teamId, Long userId) {
        return TEAM_MEMBERS_PREFIX + teamId + ":v" + version(TEAM_MEMBERS_VERSION_PREFIX + teamId) + ":u" + userId;
    }

    public String taskBoardKey(Long teamId, Long userId) {
        return TASK_BOARD_PREFIX + teamId + ":v" + version(TASK_BOARD_VERSION_PREFIX + teamId) + ":u" + userId;
    }

    public String teamWorkProfileKey(Long teamId) {
        return TEAM_WORK_PROFILE_PREFIX + teamId;
    }

    public String chatHistoryKey(Long sessionId) {
        return CHAT_HISTORY_PREFIX + sessionId;
    }

    public <T> T get(String key, Class<T> type) {
        String value = getRaw(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.warn("Redis cache deserialize failed, key={}", key, e);
            delete(key);
            return null;
        }
    }

    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            put(key, loaded, ttl);
        }
        return loaded;
    }

    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        String value = getRaw(key);
        if (value != null) {
            try {
                return objectMapper.readValue(value, type);
            } catch (Exception e) {
                log.warn("Redis cache deserialize failed, key={}", key, e);
                delete(key);
            }
        }
        T loaded = loader.get();
        if (loaded != null) {
            put(key, loaded, ttl);
        }
        return loaded;
    }

    public void put(String key, Object value, Duration ttl) {
        if (value == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Redis cache write failed, key={}", key, e);
        }
    }

    public String getRaw(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis cache read failed, key={}", key, e);
            return null;
        }
    }

    public void putRaw(String key, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Redis cache write failed, key={}", key, e);
        }
    }

    public void evictTeamBaseAfterCommit(Long teamId) {
        runAfterCommit(() -> delete(teamBaseKey(teamId)));
    }

    public void evictTeamMembersAfterCommit(Long teamId) {
        runAfterCommit(() -> bumpVersion(TEAM_MEMBERS_VERSION_PREFIX + teamId));
    }

    public void evictTaskBoardAfterCommit(Long teamId) {
        runAfterCommit(() -> bumpVersion(TASK_BOARD_VERSION_PREFIX + teamId));
    }

    public void evictTeamWorkProfileAfterCommit(Long teamId) {
        runAfterCommit(() -> delete(teamWorkProfileKey(teamId)));
    }

    public void evictChatHistory(Long sessionId) {
        delete(chatHistoryKey(sessionId));
    }

    public void evictShortTermChatMemory(Long sessionId) {
        delete(CHAT_SHORT_TERM_MEMORY_PREFIX + sessionId);
    }

    private String version(String key) {
        String value = getRaw(key);
        return value == null || value.isBlank() ? "1" : value;
    }

    private void bumpVersion(String key) {
        try {
            Long value = stringRedisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                stringRedisTemplate.expire(key, Duration.ofDays(30));
            }
        } catch (Exception e) {
            log.warn("Redis cache version bump failed, key={}", key, e);
        }
    }

    private void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis cache delete failed, key={}", key, e);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
