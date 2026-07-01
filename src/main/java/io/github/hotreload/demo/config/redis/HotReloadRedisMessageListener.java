package io.github.hotreload.demo.config.redis;

import com.alibaba.fastjson.JSONObject;
import io.github.hotreload.demo.core.cluster.HotReloadConstants;
import io.github.hotreload.demo.core.message.HotReloadDiscoverMessage;
import io.github.hotreload.demo.core.message.HotReloadTaskMessage;
import io.github.hotreload.demo.service.HotReloadClusterService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 热重载 Redis 主题监听器。
 * <p>
 * 项目使用一个 Redis 主题承载节点发现和任务通知消息，通过 messageType 区分具体处理逻辑。
 */
@Slf4j
@Component
public class HotReloadRedisMessageListener implements MessageListener {

    private final HotReloadClusterService hotReloadClusterService;

    /**
     * 构造热重载 Redis 监听器。
     *
     * @param hotReloadClusterService 集群热重载业务服务
     */
    public HotReloadRedisMessageListener(HotReloadClusterService hotReloadClusterService) {
        this.hotReloadClusterService = hotReloadClusterService;
    }

    /**
     * 接收 Redis 发布订阅消息并按消息类型分发。
     *
     * @param message Redis 消息体
     * @param pattern 匹配到的主题模式
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        JSONObject jsonObject = getJsonObject(message);
        if (jsonObject == null) {
            return;
        }
        try {
            String messageType = jsonObject.getString("messageType");
            if (StringUtils.equals(HotReloadConstants.MESSAGE_TYPE_DISCOVER_REQUEST, messageType)) {
                HotReloadDiscoverMessage discoverMessage =
                        JSONObject.toJavaObject(jsonObject, HotReloadDiscoverMessage.class);
                hotReloadClusterService.reportDiscover(discoverMessage);
                return;
            }
            if (StringUtils.equals(HotReloadConstants.MESSAGE_TYPE_RELOAD_TASK_NOTIFY, messageType)) {
                HotReloadTaskMessage taskMessage = JSONObject.toJavaObject(jsonObject, HotReloadTaskMessage.class);
                hotReloadClusterService.receiveReloadTask(taskMessage.getTaskId());
                return;
            }
            if (StringUtils.equals(HotReloadConstants.MESSAGE_TYPE_STOP_RECOVERY_TASK_NOTIFY, messageType)) {
                HotReloadTaskMessage taskMessage = JSONObject.toJavaObject(jsonObject, HotReloadTaskMessage.class);
                hotReloadClusterService.receiveStopRecoveryTask(taskMessage.getTaskId());
                return;
            }
            log.warn("忽略未知热重载消息，messageType={}，body={}", messageType, jsonObject);
        } catch (Exception e) {
            log.error("处理热重载 Redis 消息失败，body={}", jsonObject, e);
        }
    }

    /**
     * 兼容 RedisTemplate 默认 JDK 序列化和纯文本 JSON 两种消息格式。
     *
     * @param message Redis 消息体
     * @return JSON 消息对象，解析失败时返回 null
     */
    private JSONObject getJsonObject(Message message) {
        try (ObjectInputStream objectInputStream =
                     new ObjectInputStream(new ByteArrayInputStream(message.getBody()))) {
            Object value = objectInputStream.readObject();
            if (value instanceof JSONObject) {
                return (JSONObject) value;
            }
            return JSONObject.parseObject(JSONObject.toJSONString(value));
        } catch (Exception ignored) {
            return parseTextMessage(message);
        }
    }

    /**
     * 按 UTF-8 文本 JSON 解析 Redis 消息。
     *
     * @param message Redis 消息体
     * @return JSON 消息对象，解析失败时返回 null
     */
    private JSONObject parseTextMessage(Message message) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            return JSONObject.parseObject(body);
        } catch (Exception e) {
            log.error("热重载 Redis 消息反序列化失败", e);
            return null;
        }
    }
}
