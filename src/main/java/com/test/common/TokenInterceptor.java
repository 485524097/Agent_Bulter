package com.test.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final String TOKEN_KEY_PREFIX = "login:token:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");

        if (token == null || token.trim().isEmpty()) {
            writeUnauthorized(response, "未登录，请先登录");
            return false;
        }

        // 兼容 Authorization: Bearer xxxxx
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        String userIdStr = stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);

        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            writeUnauthorized(response, "登录已过期，请重新登录");
            return false;
        }

        Long userId = Long.valueOf(userIdStr);

        UserContext.setUserId(userId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(401);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String json = "{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}";
        response.getWriter().write(json);
    }
}