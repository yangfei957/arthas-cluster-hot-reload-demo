package io.github.hotreload.demo.config.reload;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 单机热重载接口访问校验器。
 * <p>
 * Demo 使用请求头 hot-reload-secret 做简单保护，生产项目应替换为自己的登录态、权限或网关鉴权。
 */
@Slf4j
@Component
public class HotReloadAccessGuard {

    private final HotReloadProperties hotReloadProperties;

    /**
     * 构造访问校验器。
     *
     * @param hotReloadProperties 热重载配置
     */
    public HotReloadAccessGuard(HotReloadProperties hotReloadProperties) {
        this.hotReloadProperties = hotReloadProperties;
    }

    /**
     * 校验请求头中的热重载密钥。
     *
     * @param request HTTP 请求
     * @return true 表示允许访问单机热重载接口
     */
    public boolean check(HttpServletRequest request) {
        String key = request.getHeader("hot-reload-secret");
        boolean valid = StringUtils.isNotBlank(key) && hotReloadProperties.getSecretKey().equals(key);
        log.info("热重载测试接口鉴权，ip={}，result={}", request.getRemoteAddr(), valid ? "通过" : "拒绝");
        return valid;
    }
}
