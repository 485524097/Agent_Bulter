package com.test.mq.consumer;

import com.test.dto.BudgetWarningResult;
import com.test.mq.RabbitMqConstants;
import com.test.mq.event.ExpenseRecordCreatedEvent;
import com.test.service.BudgetWarningService;
import com.test.service.UserNotificationService;
import com.test.websocket.NotificationWebSocketHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExpenseRecordEventConsumer {

    @Autowired
    private BudgetWarningService budgetWarningService;

    @Autowired
    private UserNotificationService userNotificationService;

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @RabbitListener(
            queues = RabbitMqConstants.EXPENSE_RECORD_CREATED_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void handleRecordCreatedEvent(ExpenseRecordCreatedEvent event) {

        if (event == null) {
            return;
        }

        System.out.println("收到记账成功 MQ 消息：recordId=" + event.getRecordId()
                + ", userId=" + event.getUserId()
                + ", recordType=" + event.getRecordType()
                + ", category=" + event.getCategory()
                + ", amount=" + event.getAmount());

        if (!"EXPENSE".equalsIgnoreCase(event.getRecordType())) {
            return;
        }

        List<BudgetWarningResult> warningResults = budgetWarningService.checkAfterExpense(
                event.getUserId(),
                event.getCategory()
        );

        if (warningResults == null || warningResults.isEmpty()) {
            System.out.println("本次记账未触发预算预警，recordId=" + event.getRecordId());
            return;
        }

        for (BudgetWarningResult warningResult : warningResults) {
            if (warningResult == null || !Boolean.TRUE.equals(warningResult.getWarning())) {
                continue;
            }

            System.out.println("异步预算预警触发：recordId=" + event.getRecordId()
                    + ", category=" + warningResult.getCategory()
                    + ", level=" + warningResult.getLevel()
                    + ", message=" + warningResult.getMessage());
            String title = "预算预警";
            String content = warningResult.getMessage();

            Long notificationId = userNotificationService.createNotification(
                    event.getUserId(),
                    title,
                    content,
                    "BUDGET_WARNING",
                    event.getRecordId()
            );

            String pushMessage = "{"
                    + "\"type\":\"BUDGET_WARNING\","
                    + "\"notificationId\":" + notificationId + ","
                    + "\"recordId\":" + event.getRecordId() + ","
                    + "\"category\":\"" + warningResult.getCategory() + "\","
                    + "\"level\":\"" + warningResult.getLevel() + "\","
                    + "\"content\":\"" + content.replace("\"", "\\\"") + "\""
                    + "}";

            notificationWebSocketHandler.sendToUser(event.getUserId(), pushMessage);
        }
    }
}