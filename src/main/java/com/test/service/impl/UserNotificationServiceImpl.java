package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.test.dto.UserNotificationDTO;
import com.test.entity.UserNotification;
import com.test.mapper.UserNotificationMapper;
import com.test.service.UserNotificationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserNotificationServiceImpl
        extends ServiceImpl<UserNotificationMapper, UserNotification>
        implements UserNotificationService {

    @Override
    public Long createNotification(Long userId,
                                   String title,
                                   String content,
                                   String notifyType,
                                   Long businessId) {

        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        UserNotification notification = new UserNotification();

        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setNotifyType(notifyType);
        notification.setBusinessId(businessId);
        notification.setReadStatus(0);
        notification.setDeleted(0);

        this.save(notification);

        return notification.getId();
    }

    @Override
    public List<UserNotificationDTO> listNotifications(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        QueryWrapper<UserNotification> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByAsc("read_status")
                .orderByDesc("created_time");

        List<UserNotification> notifications = this.list(queryWrapper);

        List<UserNotificationDTO> result = new ArrayList<>();

        for (UserNotification notification : notifications) {
            UserNotificationDTO dto = new UserNotificationDTO();

            dto.setId(notification.getId());
            dto.setTitle(notification.getTitle());
            dto.setContent(notification.getContent());
            dto.setNotifyType(notification.getNotifyType());
            dto.setBusinessId(notification.getBusinessId());
            dto.setReadStatus(notification.getReadStatus());
            dto.setCreatedTime(notification.getCreatedTime());

            result.add(dto);
        }

        return result;
    }

    @Override
    public Long countUnread(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        QueryWrapper<UserNotification> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("read_status", 0)
                .eq("deleted", 0);

        return this.count(queryWrapper);
    }

    @Override
    public void markRead(Long userId, Long notificationId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        if (notificationId == null) {
            throw new RuntimeException("通知ID不能为空");
        }

        QueryWrapper<UserNotification> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("id", notificationId)
                .eq("user_id", userId)
                .eq("deleted", 0);

        UserNotification notification = this.getOne(queryWrapper);

        if (notification == null) {
            throw new RuntimeException("通知不存在");
        }

        notification.setReadStatus(1);

        this.updateById(notification);
    }

    @Override
    public void markAllRead(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        UserNotification update = new UserNotification();
        update.setReadStatus(1);

        QueryWrapper<UserNotification> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("read_status", 0)
                .eq("deleted", 0);

        this.update(update, queryWrapper);
    }
}