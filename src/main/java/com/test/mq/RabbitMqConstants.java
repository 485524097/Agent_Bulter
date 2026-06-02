package com.test.mq;

public class RabbitMqConstants {

    /**
     * 记账事件交换机
     */
    public static final String EXPENSE_EXCHANGE = "butler.expense.exchange";

    /**
     * 记账成功队列
     */
    public static final String EXPENSE_RECORD_CREATED_QUEUE = "butler.expense.record.created.queue";

    /**
     * 记账成功路由键
     */
    public static final String EXPENSE_RECORD_CREATED_ROUTING_KEY = "expense.record.created";
}