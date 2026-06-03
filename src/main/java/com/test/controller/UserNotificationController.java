package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.UserNotificationDTO;
import com.test.service.UserNotificationService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification")
public class UserNotificationController {

    @Autowired
    private UserNotificationService userNotificationService;

    /**
     * 查询通知列表
     */
    @GetMapping("/list")
    public ResultVO<List<UserNotificationDTO>> listNotifications() {

        Long userId = UserContext.getUserId();

        List<UserNotificationDTO> list = userNotificationService.listNotifications(userId);

        return ResultVOUtil.success(list);
    }

    /**
     * 查询未读通知数量
     */
    @GetMapping("/unread/count")
    public ResultVO<Long> countUnread() {

        Long userId = UserContext.getUserId();

        Long count = userNotificationService.countUnread(userId);

        return ResultVOUtil.success(count);
    }

    /**
     * 标记单条通知已读
     */
    @PutMapping("/{notificationId}/read")
    public ResultVO markRead(@PathVariable Long notificationId) {

        Long userId = UserContext.getUserId();

        userNotificationService.markRead(userId, notificationId);

        return ResultVOUtil.success("通知已标记为已读");
    }

    /**
     * 一键全部已读
     */
    @PutMapping("/read/all")
    public ResultVO markAllRead() {

        Long userId = UserContext.getUserId();

        userNotificationService.markAllRead(userId);

        return ResultVOUtil.success("全部通知已标记为已读");
    }
}  