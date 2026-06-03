package com.test.websocket;

import com.test.service.UserTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class NotificationWebSocketInterceptor implements HandshakeInterceptor {

    @Autowired
    private UserTokenService userTokenService;

    @Override
    public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,
                                   org.springframework.http.server.ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String query = request.getURI().getQuery();

        if (query == null || !query.contains("token=")) {
            return false;
        }

        String token = query.replace("token=", "").trim();

        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        Long userId = userTokenService.getUserIdByToken(token);

        if (userId == null) {
            return false;
        }

        attributes.put("userId", userId);

        return true;
    }

    @Override
    public void afterHandshake(org.springframework.http.server.ServerHttpRequest request,
                               org.springframework.http.server.ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}