package com.test.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.common.UserContext;
import com.test.dto.AgentChatVO;
import com.test.entity.AgentApiLog;
import com.test.service.AgentApiLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

@Aspect
@Component
public class AgentApiLogAspect {

    @Autowired
    private AgentApiLogService agentApiLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 拦截 ButlerAgentController 下所有方法
     */
    @Around("execution(* com.test.controller.BulterAgentController.*(..))")
    public Object recordAgentApiLog(ProceedingJoinPoint joinPoint) throws Throwable {

        long startTime = System.currentTimeMillis();

        AgentApiLog log = new AgentApiLog();

        fillBasicInfo(log, joinPoint);

        try {
            Object result = joinPoint.proceed();

            long cost = System.currentTimeMillis() - startTime;

            log.setSuccess(1);
            log.setCostMs(cost);
            log.setErrorMsg(null);
            log.setCreatedTime(LocalDateTime.now());

            agentApiLogService.save(log);

            return result;

        } catch (Throwable e) {

            long cost = System.currentTimeMillis() - startTime;

            log.setSuccess(0);
            log.setCostMs(cost);
            log.setErrorMsg(e.getMessage());
            log.setCreatedTime(LocalDateTime.now());

            agentApiLogService.save(log);

            throw e;
        }
    }

    private void fillBasicInfo(AgentApiLog log, ProceedingJoinPoint joinPoint) {

        try {
            log.setUserId(UserContext.getUserId());
        } catch (Exception e) {
            log.setUserId(null);
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        log.setApiName(signature.getMethod().getName());

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            log.setRequestUri(request.getRequestURI());
            log.setRequestMethod(request.getMethod());
        }

        Object[] args = joinPoint.getArgs();

        if (args != null && args.length > 0) {
            for (Object arg : args) {

                if (arg instanceof AgentChatVO chatVO) {
                    log.setSessionId(chatVO.getSessionId());
                    log.setRequestContent(toJson(chatVO));
                    return;
                }

                if (arg instanceof SseEmitter) {
                    continue;
                }
            }

            log.setRequestContent(toJson(args));
        }
    }

    private String toJson(Object object) {
        try {
            String json = objectMapper.writeValueAsString(object);

            if (json.length() > 2000) {
                return json.substring(0, 2000);
            }

            return json;
        } catch (Exception e) {
            return String.valueOf(object);
        }
    }
}