package com.test.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler implements WebSocketHandler {

    private static final Map<Long, WebSocketSession> SESSION_MAP = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userIdObj = session.getAttributes().get("userId");

        if (userIdObj instanceof Long userId) {
            SESSION_MAP.put(userId, session);
            System.out.println("WebSocket 通知连接建立，userId=" + userId);
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        // 第一版不处理客户端消息
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        removeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        removeSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public void sendToUser(Long userId, String message) {

        WebSocketSession session = SESSION_MAP.get(userId);

        if (session == null || !session.isOpen()) {
            System.out.println("用户不在线，无法实时推送，userId=" + userId);
            return;
        }

        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            System.out.println("WebSocket 推送失败，userId=" + userId + "，原因：" + e.getMessage());
        }
    }

    private void removeSession(WebSocketSession session) {
        if (session == null) {
            return;
        }

        Object userIdObj = session.getAttributes().get("userId");

        if (userIdObj instanceof Long userId) {
            SESSION_MAP.remove(userId);
            System.out.println("WebSocket 通知连接关闭，userId=" + userId);
        }
    }
}