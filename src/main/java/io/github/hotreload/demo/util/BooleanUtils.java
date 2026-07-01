package io.github.hotreload.demo.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 布尔开关解析工具。
 * <p>
 * 数据库配置中常见的 Y、ON、true、1 都会被识别为开启。
 */
public final class BooleanUtils {

    /**
     * 工具类不允许实例化。
     */
    private BooleanUtils() {
    }

    /**
     * 判断对象是否表示开启。
     *
     * @param value 待判断对象
     * @return true 表示开启
     */
    public static boolean isTrue(Object value) {
        return value != null && isTrue(value.toString());
    }

    /**
     * 判断字符串是否表示开启。
     *
     * @param value 待判断字符串
     * @return true 表示开启
     */
    public static boolean isTrue(String value) {
        return StringUtils.isNotBlank(value)
                && ("Y".equalsIgnoreCase(value)
                || "ON".equalsIgnoreCase(value)
                || "true".equalsIgnoreCase(value)
                || "1".equals(value));
    }

    /**
     * 判断对象是否表示关闭。
     *
     * @param value 待判断对象
     * @return true 表示关闭
     */
    public static boolean isFalse(Object value) {
        return !isTrue(value);
    }
}
