package io.github.hotreload.demo.util;

import org.springframework.asm.ClassReader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * 热重载通用工具。
 * <p>
 * 提供文件读取、摘要计算和 class 文件类名解析等基础能力。
 */
public final class HotReloadUtils {

    /**
     * 工具类不允许实例化。
     */
    private HotReloadUtils() {
    }

    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * 计算文件内容的 SHA-256 摘要。
     *
     * @param bytes 文件内容
     * @return 小写十六进制摘要
     */
    public static String sha256(byte[] bytes) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(bytes);
            byte[] digest = messageDigest.digest();
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(HEX_DIGITS[(b >> 4) & 0x0f]);
                builder.append(HEX_DIGITS[b & 0x0f]);
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算热重载文件摘要失败", e);
        }
    }

    /**
     * 读取输入流全部字节。
     *
     * @param in 输入流
     * @return 输入流内容
     * @throws Exception 读取失败时抛出
     */
    public static byte[] readBytes(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int length;
        while ((length = in.read(buffer)) != -1) {
            bos.write(buffer, 0, length);
        }
        return bos.toByteArray();
    }

    /**
     * 按指定字符集读取输入流文本。
     *
     * @param in      输入流
     * @param charset 字符集名称
     * @return 文本内容
     * @throws Exception 读取失败时抛出
     */
    public static String readString(InputStream in, String charset) throws Exception {
        return new String(readBytes(in), charset);
    }

    /**
     * 从 class 文件字节码中解析完整类名。
     *
     * @param classBytes class 文件内容
     * @return 完整类名
     */
    public static String parseClassName(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        return reader.getClassName().replace('/', '.');
    }
}
