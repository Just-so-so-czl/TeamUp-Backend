package com.czl.teamupbackend.config;

import com.czl.teamupbackend.ai.RedisChatMemory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class AiChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        return new RedisChatMemory(stringRedisTemplate, objectMapper);
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}

