package io.github.hotreload.demo.config.redis;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 发布订阅消息发送器。
 * <p>
 * 集群热重载通过它发布节点发现消息和任务通知消息。
 */
@Slf4j
@Component
public class RedisPublisher {

    private final RedisTemplate<String, Object> hotReloadRedisTemplate;

    /**
     * 构造 Redis 消息发送器。
     *
     * @param hotReloadRedisTemplate 热重载使用的 RedisTemplate
     */
    public RedisPublisher(RedisTemplate<String, Object> hotReloadRedisTemplate) {
        this.hotReloadRedisTemplate = hotReloadRedisTemplate;
    }

    /**
     * 向指定 Redis 主题发布 JSON 消息。
     *
     * @param topic       Redis 主题
     * @param messageBody 消息内容
     */
    public void pushMessage(String topic, JSONObject messageBody) {
        try {
            hotReloadRedisTemplate.convertAndSend(topic, messageBody);
        } catch (RuntimeException e) {
            log.error("Redis 发布订阅消息发送失败，topic={}，body={}", topic, messageBody, e);
            throw e;
        }
    }
}
