package com.test.mq.producer;

import com.test.mq.RabbitMqConstants;
import com.test.mq.event.ExpenseRecordCreatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExpenseRecordEventProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendRecordCreatedEvent(ExpenseRecordCreatedEvent event) {

        if (event == null) {
            return;
        }

        rabbitTemplate.convertAndSend(
                RabbitMqConstants.EXPENSE_EXCHANGE,
                RabbitMqConstants.EXPENSE_RECORD_CREATED_ROUTING_KEY,
                event
        );

        System.out.println("已发送记账成功 MQ 消息：" + event);
    }
}