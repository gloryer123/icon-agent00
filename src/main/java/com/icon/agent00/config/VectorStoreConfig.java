package com.icon.agent00.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

@Configuration
public class VectorStoreConfig {
    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;
    @Value("${spring.data.redis.password}")
    private String redisPassword;
    @Value("${spring.data.redis.database}")
    private int database;
    @Bean
    public JedisPooled jedisPooled() {
        // 使用 JedisClientConfig 显式指定 password 和 database
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .password(redisPassword != null && !redisPassword.trim().isEmpty() ? redisPassword : null)
                .database(database)
                .build();

        return new JedisPooled(new HostAndPort(redisHost, redisPort), clientConfig);
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("school-index")
                .prefix("school:vec:")
                // 【核心】必须在这里注册你 ETL 写入时用到的元数据，否则将来无法精确搜索
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("country"),
                        RedisVectorStore.MetadataField.numeric("is_public"),
                        RedisVectorStore.MetadataField.numeric("tuition"),   // 学费预算 (数值，便于实现 "tuition < 200000" 的过滤)
                        RedisVectorStore.MetadataField.tag("region")
                )
                .initializeSchema(true)
                .build();
    }

}
