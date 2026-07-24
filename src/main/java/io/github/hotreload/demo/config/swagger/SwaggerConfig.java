package io.github.hotreload.demo.config.swagger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * Swagger 接口文档配置。
 * <p>
 * Demo 面向热重载学习者开放接口文档，只扫描本项目的 controller 包，
 * 让用户可以直接在 Swagger UI 中查看节点发现、任务创建和单机热重载接口。
 */
@Configuration
public class SwaggerConfig {

    /**
     * 创建 Swagger 文档分组。
     *
     * @return 热重载 Demo 的接口文档定义
     */
    @Bean
    public Docket hotReloadApi() {
        return new Docket(DocumentationType.OAS_30)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("io.github.hotreload.demo.controller"))
                .paths(PathSelectors.any())
                .build();
    }

    /**
     * 定义 Swagger 页面展示的项目说明。
     *
     * @return 接口文档基础信息
     */
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("Byte Buddy Agent Cluster Hot Reload Demo")
                .description("基于 Byte Buddy Agent、Redis 广播和数据库执行日志的集群热重载示例接口")
                .version("0.1.0")
                .build();
    }
}
