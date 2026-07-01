package io.github.hotreload.demo.config.reload;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 热重载配置属性。
 * <p>
 * 配置项来自 application.yml 的 hot-reload 前缀，用于控制 Redis 主题、单机接口密钥、Arthas 启动等待时间和数据库方言。
 */
@Component
@ConfigurationProperties(prefix = "hot-reload")
public class HotReloadProperties {

    /**
     * 当前模块使用的 Redis 广播主题。
     */
    private String redisTopic = "HOT_RELOAD_TOPIC";

    /**
     * 单机测试接口使用的简单密钥。
     */
    private String secretKey = "demo-hot-reload";

    /**
     * 服务启动后等待 Arthas 初始化的时间。
     */
    private long arthasInitWaitMs = 5000L;

    /**
     * MyBatis-Plus 分页插件使用的数据库类型。
     */
    private String databaseType = "MYSQL";

    /**
     * 获取 Redis 广播主题。
     *
     * @return Redis 主题
     */
    public String getRedisTopic() {
        return redisTopic;
    }

    /**
     * 设置 Redis 广播主题。
     *
     * @param redisTopic Redis 主题
     */
    public void setRedisTopic(String redisTopic) {
        this.redisTopic = redisTopic;
    }

    /**
     * 获取单机热重载接口密钥。
     *
     * @return 接口密钥
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * 设置单机热重载接口密钥。
     *
     * @param secretKey 接口密钥
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 获取服务启动后等待 Arthas 初始化的时间。
     *
     * @return 等待时间，单位毫秒
     */
    public long getArthasInitWaitMs() {
        return arthasInitWaitMs;
    }

    /**
     * 设置服务启动后等待 Arthas 初始化的时间。
     *
     * @param arthasInitWaitMs 等待时间，单位毫秒
     */
    public void setArthasInitWaitMs(long arthasInitWaitMs) {
        this.arthasInitWaitMs = arthasInitWaitMs;
    }

    /**
     * 获取数据库类型。
     *
     * @return 数据库类型
     */
    public String getDatabaseType() {
        return databaseType;
    }

    /**
     * 设置数据库类型。
     *
     * @param databaseType 数据库类型
     */
    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }
}
