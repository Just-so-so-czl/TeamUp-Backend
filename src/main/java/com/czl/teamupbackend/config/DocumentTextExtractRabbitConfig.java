package com.czl.teamupbackend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 资料文档文本提取队列。
 */
@Configuration
public class DocumentTextExtractRabbitConfig {

    public static final String EXCHANGE = "teamup.document.extract.exchange";
    public static final String QUEUE = "teamup.document.extract.queue";
    public static final String ROUTING_KEY = "teamup.document.extract.resource";

    @Bean
    public DirectExchange documentTextExtractExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue documentTextExtractQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding documentTextExtractBinding(
        DirectExchange documentTextExtractExchange,
        Queue documentTextExtractQueue
    ) {
        return BindingBuilder.bind(documentTextExtractQueue)
            .to(documentTextExtractExchange)
            .with(ROUTING_KEY);
    }
}
