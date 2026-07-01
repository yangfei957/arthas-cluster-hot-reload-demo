package io.github.hotreload.demo.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.Inet4Address;
import java.net.UnknownHostException;

/**
 * 本机运行环境工具。
 * <p>
 * 集群热重载使用本机 IP 作为节点标识，容器环境下通常会得到 Pod IP。
 */
@Slf4j
public final class SystemUtils {

    /**
     * 工具类不允许实例化。
     */
    private SystemUtils() {
    }

    /**
     * 获取本机 IP。容器podIP
     *
     * @return 本机 IPv4 地址，获取失败时返回 localhost
     */
    public static String getLocalIP() {
        try {
            String localIp = Inet4Address.getLocalHost().getHostAddress();
            log.info("获取本机 IP：{}", localIp);
            return localIp;
        } catch (UnknownHostException e) {
            log.error("获取本机 IP 失败", e);
            return "localhost";
        }
    }

    /**
     * 获取本机端口。
     *
     * @return 环境变量 PORT 指定的端口，未配置时返回 demo 默认端口
     */
    public static int getLocalPort() {
        try {
            String port = System.getenv("PORT");
            if (StringUtils.isNotBlank(port)) {
                return Integer.parseInt(port);
            }
        } catch (Exception e) {
            log.error("获取本机端口失败", e);
        }
        return 31001;
    }

    /**
     * 判断传入 IP 是否匹配当前节点。
     *
     * @param sysIp 目标 IP，星号表示匹配全部
     * @return true 表示匹配当前节点
     */
    public static boolean checkIpEq(String sysIp) {
        if (StringUtils.isBlank(sysIp)) {
            return false;
        }
        return "*".equals(sysIp) || sysIp.equals(getLocalIP());
    }

    /**
     * 判断当前系统是否为 Windows。
     *
     * @return true 表示 Windows 系统
     */
    public static boolean isWindowsOS() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().contains("windows");
    }
}
