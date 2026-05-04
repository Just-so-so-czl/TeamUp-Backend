package com.czl.teamupbackend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskReminderRabbitConfig {

    public static final String EXCHANGE = "teamup.task.reminder.exchange";
    public static final String QUEUE = "teamup.task.reminder.queue";
    public static final String ROUTING_KEY = "teamup.task.reminder.send";

    @Bean
    public DirectExchange taskReminderExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue taskReminderQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding taskReminderBinding(DirectExchange taskReminderExchange, Queue taskReminderQueue) {
        return BindingBuilder.bind(taskReminderQueue).to(taskReminderExchange).with(ROUTING_KEY);
    }
}

