package io.github.hotreload.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Arthas 集群热重载 Demo 启动类。
 * <p>
 * 启动后会初始化 Spring Boot、Redis 监听、MyBatis 和 Arthas starter，用于演示单机与集群热重载流程。
 */
@Slf4j
@SpringBootApplication
public class HotReloadApplication {

    /**
     * 应用启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(HotReloadApplication.class, args);
        log.info("App deploy success ^_^  ");
    }
}
