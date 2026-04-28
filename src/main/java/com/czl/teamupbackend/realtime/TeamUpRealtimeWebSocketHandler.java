package com.czl.teamupbackend.realtime;

import com.czl.teamupbackend.commen.jwt.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * TeamUp 实时消息 WebSocket 处理器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TeamUpRealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_USER_ID = "teamup_user_id";

    private final JwtTokenUtil jwtTokenUtil;
    private final OnlineUserSessionManager onlineUserSessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (!StringUtils.hasText(token)) {
            log.warn("WebSocket connection rejected: missing token, sessionId={}", session.getId());
            closeSilently(session, CloseStatus.NOT_ACCEPTABLE.withReason("Missing token"));
            return;
        }
        try {
            Long userId = jwtTokenUtil.getUserId(token);
            session.getAttributes().put(ATTR_USER_ID, userId);
            onlineUserSessionManager.put(userId, session);
            log.info("WebSocket connected, userId={}, sessionId={}", userId, session.getId());
        } catch (Exception e) {
            log.warn("WebSocket connection rejected: invalid token, sessionId={}, error={}",
                session.getId(), e.getMessage());
            closeSilently(session, CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            onlineUserSessionManager.remove(userId);
            log.info("WebSocket disconnected, userId={}, sessionId={}, status={}",
                userId, session.getId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = getUserId(session);
        if (userId != null) {
            onlineUserSessionManager.remove(userId);
        }
        closeSilently(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 当前仅服务端主动推送，这里可按需扩展客户端消息处理
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri() == null ? null : session.getUri().getQuery();
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    private Long getUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(ATTR_USER_ID);
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private void closeSilently(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception e) {
            log.warn("WebSocket close failed: {}", e.getMessage());
        }
    }
}
