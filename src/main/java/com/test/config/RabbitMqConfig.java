package com.test.config;

import com.test.mq.RabbitMqConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    /**
     * JSON 消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 生产者使用 JSON 转换器
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }

    /**
     * 消费者使用 JSON 转换器
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }

    @Bean
    public DirectExchange expenseExchange() {
        return new DirectExchange(
                RabbitMqConstants.EXPENSE_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public Queue expenseRecordCreatedQueue() {
        return QueueBuilder
                .durable(RabbitMqConstants.EXPENSE_RECORD_CREATED_QUEUE)
                .build();
    }

    @Bean
    public Binding expenseRecordCreatedBinding() {
        return BindingBuilder
                .bind(expenseRecordCreatedQueue())
                .to(expenseExchange())
                .with(RabbitMqConstants.EXPENSE_RECORD_CREATED_ROUTING_KEY);
    }
}