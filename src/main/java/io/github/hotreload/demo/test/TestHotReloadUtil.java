package io.github.hotreload.demo.test;

/**
 * class 热重载测试工具类。
 * <p>
 * 该类不由 Spring 容器管理，适合验证工具类或常量类方法体修改后的热重载效果。
 */
public final class TestHotReloadUtil {

    /**
     * 工具类不允许实例化。
     */
    private TestHotReloadUtil() {
    }

    /**
     * 判断字符串是否为空。
     *
     * @param value 测试字符串
     * @return true 表示为空
     */
    public static boolean isEmpty(String value) {
        return value == null;
    }
}
