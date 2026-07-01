package io.github.hotreload.demo.core.cluster;

import io.github.hotreload.demo.util.SystemUtils;
import io.github.hotreload.demo.vo.HotReloadNodeVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;

/**
 * 当前服务实例的热重载节点信息。
 * <p>
 * 节点通过 spring.application.name、active profile 和本机 IP 构成集群热重载的目标定位信息。
 */
@Slf4j
@Component
public class HotReloadLocalNode {

    @Value("${spring.profiles.active:default}")
    private String profile;

    @Value("${spring.application.name:}")
    private String springApplicationName;

    private String ip;

    /**
     * 初始化当前节点 IP，并输出节点标识信息。容器里为podIP
     */
    @PostConstruct
    public void init() {
        this.ip = SystemUtils.getLocalIP();
        log.info("热重载节点初始化完成，appName={}，env={}，ip={}", getAppName(), getEnv(), ip);
    }

    /**
     * 构建当前节点的 Redis 注册信息。
     *
     * @return 当前节点信息
     */
    public HotReloadNodeVO currentNode() {
        HotReloadNodeVO nodeVO = new HotReloadNodeVO();
        nodeVO.setAppName(getAppName());
        nodeVO.setEnv(getEnv());
        nodeVO.setIp(ip);
        nodeVO.setUpdateTime(new Date());
        nodeVO.setHotReloadStatus(HotReloadConstants.HOT_RELOAD_STATUS_IDLE);
        return nodeVO;
    }

    /**
     * 读取应用名称。
     *
     * @return spring.application.name 配置值
     */
    public String getAppName() {
        String appName = StringUtils.trimToEmpty(springApplicationName);
        if (StringUtils.isBlank(appName)) {
            throw new IllegalStateException("spring.application.name 不能为空");
        }
        return appName;
    }

    /**
     * 读取当前运行环境。
     *
     * @return active profile 的第一个值，未配置时返回 default
     */
    public String getEnv() {
        String activeProfile = StringUtils.defaultIfBlank(profile, "default");
        return StringUtils.defaultIfBlank(StringUtils.substringBefore(activeProfile, ","), "default");
    }

    /**
     * 读取当前节点 IP。
     *
     * @return 本机 IP，容器环境下一般是 Pod IP
     */
    public String getIp() {
        return ip;
    }
}
