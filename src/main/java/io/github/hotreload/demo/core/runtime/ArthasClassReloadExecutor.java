package io.github.hotreload.demo.core.runtime;

import io.github.hotreload.demo.util.HotReloadUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Arthas class 重载执行器。
 * <p>
 * 该组件负责把上传的 class 字节写入临时文件，并通过 Arthas HTTP API 执行 retransform 命令。
 */
@Slf4j
@Component
public class ArthasClassReloadExecutor {

    private static final int CONNECT_TIMEOUT = 3000;
    private static final int MAX_RETRY = 3;
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long ARTHAS_EXEC_TIMEOUT_MS = 120000L;
    private static final int READ_TIMEOUT = 150000;

    private final String arthasApi;

    /**
     * 构造 Arthas HTTP API 调用地址。
     *
     * @param httpPort Arthas HTTP 端口
     * @param ip       Arthas 绑定 IP
     */
    public ArthasClassReloadExecutor(@Value("${arthas.http-port}") int httpPort,
                                     @Value("${arthas.ip}") String ip) {
        this.arthasApi = "http://" + ip + ":" + httpPort + "/api";
    }

    /**
     * 使用 Arthas retransform 命令重载 class。
     *
     * @param classBytes class 文件内容
     * @return Arthas HTTP API 返回结果
     * @throws Exception 临时文件写入、Arthas 调用或重载失败时抛出
     */
    public String reloadClass(byte[] classBytes) throws Exception {
        String className = HotReloadUtils.parseClassName(classBytes);
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "");
        String relativePath = className.replace('.', File.separatorChar) + "_" + uniqueSuffix + ".class";
        File tmpFile = new File(System.getProperty("java.io.tmpdir"), relativePath);
        tmpFile.getParentFile().mkdirs();
        Files.write(tmpFile.toPath(), classBytes);

        try {
            Exception lastException = null;
            for (int i = 1; i <= MAX_RETRY; i++) {
                try {
                    String safePath = tmpFile.getAbsolutePath().replace("\\", "/");
                    String result = callArthasApi("retransform " + safePath);
                    log.info("Arthas 热重载结果：{}", result);
                    if (result.contains("\"state\":\"FAILED\"") || result.contains("\"statusCode\":-1")) {
                        throw new RuntimeException("Arthas 类重载失败：" + extractMessage(result));
                    }
                    return result;
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    lastException = e;
                    log.warn("Arthas 调用失败，第 {}/{} 次：{}", i, MAX_RETRY, e.getMessage());
                    if (i < MAX_RETRY) {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    }
                }
            }
            throw new RuntimeException("Arthas 调用失败，已重试 " + MAX_RETRY + " 次", lastException);
        } finally {
            Files.deleteIfExists(tmpFile.toPath());
        }
    }

    /**
     * 探测 Arthas HTTP API 是否已经可用。
     *
     * @return true 表示 version 命令执行成功
     */
    public boolean isReady() {
        try {
            String result = callArthasApi("version");
            boolean ready = result.contains("\"state\":\"SUCCEEDED\"");
            if (!ready) {
                log.debug("Arthas 探活响应异常：{}", result);
            }
            return ready;
        } catch (ConnectException e) {
            log.debug("Arthas 端口未就绪：{}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("Arthas 探活异常：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 调用 Arthas HTTP API 执行指定命令。
     *
     * @param command Arthas 命令文本
     * @return Arthas 返回内容
     * @throws Exception HTTP 调用失败时抛出
     */
    public String callArthasApi(String command) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(arthasApi).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        String body = String.format("{\"action\":\"exec\",\"command\":\"%s\",\"execTimeout\":%d}",
                jsonEscape(command), ARTHAS_EXEC_TIMEOUT_MS);

        try {
            byte[] payload = body.getBytes("UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(payload.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload);
                outputStream.flush();
            }
            int responseCode = connection.getResponseCode();
            try (InputStream inputStream = (responseCode >= 200 && responseCode < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream()) {
                return HotReloadUtils.readString(inputStream, "UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 从 Arthas JSON 返回中提取 message 字段，便于异常信息更直观。
     *
     * @param result Arthas 返回内容
     * @return message 字段或原始返回
     */
    private String extractMessage(String result) {
        try {
            int index = result.indexOf("\"message\":\"");
            if (index == -1) {
                return result;
            }
            int start = index + "\"message\":\"".length();
            int end = result.indexOf("\"", start);
            return end > start ? result.substring(start, end) : result;
        } catch (Exception e) {
            return result;
        }
    }

    /**
     * 转义 Arthas 命令中的 JSON 特殊字符。
     *
     * @param value 原始命令
     * @return 可安全放入 JSON 字符串的命令
     */
    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
            }
        }
        return builder.toString();
    }
}
