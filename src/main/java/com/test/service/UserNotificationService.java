package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.UserNotificationDTO;
import com.test.entity.UserNotification;

import java.util.List;

public interface UserNotificationService extends IService<UserNotification> {

    Long createNotification(Long userId,
                            String title,
                            String content,
                            String notifyType,
                            Long businessId);

    List<UserNotificationDTO> listNotifications(Long userId);

    Long countUnread(Long userId);

    void markRead(Long userId, Long notificationId);

    void markAllRead(Long userId);
}