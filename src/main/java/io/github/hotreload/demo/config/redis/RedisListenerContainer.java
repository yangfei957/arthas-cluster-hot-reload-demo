package io.github.hotreload.demo.config.redis;

import io.github.hotreload.demo.config.reload.HotReloadProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 监听容器配置。
 * <p>
 * 该配置把热重载监听器绑定到配置文件中的 Redis 主题。
 */
@Configuration
public class RedisListenerContainer {

    /**
     * 创建热重载 Redis 监听容器。
     *
     * @param redisConnectionFactory       Redis 连接工厂
     * @param hotReloadRedisMessageListener 热重载消息监听器
     * @param hotReloadProperties         热重载配置
     * @return Redis 消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer hotReloadMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            HotReloadRedisMessageListener hotReloadRedisMessageListener,
            HotReloadProperties hotReloadProperties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(hotReloadRedisMessageListener,
                new PatternTopic(hotReloadProperties.getRedisTopic()));
        return container;
    }
}
