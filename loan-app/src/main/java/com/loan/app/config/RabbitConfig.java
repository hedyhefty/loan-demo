package com.loan.app.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "loan.order.exchange";
    public static final String QUEUE = "loan.order.audit.queue";
    public static final String ROUTING_KEY = "loan.order.created";

    public static final String DLX_EXCHANGE = "loan.dlx.exchange";
    public static final String DLX_QUEUE = "loan.dlx.queue";
    public static final String DLX_ROUTING_KEY = "loan.dlx.failed";

    // 死信交换机
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // 死信队列
    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE);
    }

    // 死信队列绑定
    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }

    // 业务交换机
    @Bean
    public TopicExchange loanExchange() {
        return new TopicExchange(EXCHANGE);
    }

    // 业务队列（绑定死信属性）
    @Bean
    public Queue auditQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);
        return new Queue(QUEUE, true, false, false, args);
    }

    // 业务队列绑定
    @Bean
    public Binding binding() {
        return BindingBuilder.bind(auditQueue()).to(loanExchange()).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
