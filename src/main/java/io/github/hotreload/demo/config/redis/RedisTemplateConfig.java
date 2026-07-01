package io.github.hotreload.demo.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate 配置。
 * <p>
 * Demo 使用 Spring Data Redis 默认连接工厂，单独提供热重载发布订阅使用的 RedisTemplate。
 */
@Configuration
public class RedisTemplateConfig {

    /**
     * 保持和原项目一致的发布订阅序列化方式：key 使用字符串，value 保留 RedisTemplate 默认 JDK 序列化。
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @return 热重载发布订阅使用的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> hotReloadRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
