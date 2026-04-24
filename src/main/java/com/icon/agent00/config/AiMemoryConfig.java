package com.icon.agent00.config;


import com.alibaba.cloud.ai.memory.redis.RedissonRedisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

@Configuration
public class AiMemoryConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;
    @Value("${spring.data.redis.password}")
    private String redisPassword;
    @Value("${spring.data.redis.database}")
    private int database;



    // 创建Memory
    @Bean
    public MessageWindowChatMemory messageWindowChatMemory(RedissonRedisChatMemoryRepository redisChatMemoryRepository) {
        MessageWindowChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository) // 绑定MemoryRepository
                .maxMessages(20) // 最多保存100条消息
                .build();
        return messageWindowChatMemory;
    }

    // 创建MemoryRepository(引入官方依赖spring-ai-alibaba-starter-memory-redis)
    @Bean
    public RedissonRedisChatMemoryRepository redisChatMemoryRepository() {
        return RedissonRedisChatMemoryRepository.builder()
                .host(redisHost)
                .port(redisPort)
                .password(redisPassword)
                .database(database)
                .build();
    }

}
