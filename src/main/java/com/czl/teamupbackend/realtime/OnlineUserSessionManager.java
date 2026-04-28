package com.czl.teamupbackend.realtime;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 在线用户会话管理器
 */
@Component
@Slf4j
public class OnlineUserSessionManager {

    private final ConcurrentMap<Long, WebSocketSession> userSessionMap = new ConcurrentHashMap<>();

    public void put(Long userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        userSessionMap.put(userId, session);
        log.info("WebSocket session registered, userId={}", userId);
    }

    public void remove(Long userId) {
        if (userId == null) {
            return;
        }
        userSessionMap.remove(userId);
        log.info("WebSocket session removed, userId={}", userId);
    }

    public boolean isOnline(Long userId) {
        WebSocketSession session = userSessionMap.get(userId);
        return session != null && session.isOpen();
    }

    public void sendToUser(Long userId, String message) {
        WebSocketSession session = userSessionMap.get(userId);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.warn("WebSocket send failed, userId={}, error={}", userId, e.getMessage());
        }
    }
}

