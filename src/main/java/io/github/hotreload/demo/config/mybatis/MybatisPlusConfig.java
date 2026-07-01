package io.github.hotreload.demo.config.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.hotreload.demo.config.reload.HotReloadProperties;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Locale;

/**
 * MyBatis Plus 配置。
 * <p>
 * 提供多数据库分页插件、MyBatis databaseId 识别和基础审计字段自动填充能力。
 */
@Configuration
@MapperScan(value = {"io.github.hotreload.demo.mapper", "io.github.hotreload.demo.test"}, annotationClass = Mapper.class)
public class MybatisPlusConfig {

    private final HotReloadProperties hotReloadProperties;

    /**
     * 构造 MyBatis Plus 配置。
     *
     * @param hotReloadProperties 热重载配置属性
     */
    public MybatisPlusConfig(HotReloadProperties hotReloadProperties) {
        this.hotReloadProperties = hotReloadProperties;
    }

    /**
     * 创建 MyBatis Plus 分页拦截器。
     *
     * @return 分页拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(resolveDbType()));
        return interceptor;
    }

    /**
     * 设置 MyBatis databaseId。
     * <p>
     * 这里直接使用配置项，不通过数据库连接读取产品名，避免数据库未启动时提前创建 SqlSessionFactory 失败。
     *
     * @return MyBatis 配置自定义器
     */
    @Bean
    public ConfigurationCustomizer databaseIdConfigurationCustomizer() {
        return configuration -> configuration.setDatabaseId(resolveDatabaseId());
    }

    /**
     * 解析配置中的数据库类型。
     *
     * @return MyBatis-Plus 数据库类型
     */
    private DbType resolveDbType() {
        String databaseType = StringUtils.defaultIfBlank(hotReloadProperties.getDatabaseType(), "MYSQL")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("-", "_");
        if ("POSTGRESQL".equals(databaseType) || "POSTGRES".equals(databaseType)) {
            return DbType.POSTGRE_SQL;
        }
        if ("SQLSERVER".equals(databaseType) || "MSSQL".equals(databaseType)) {
            return DbType.SQL_SERVER;
        }
        return DbType.getDbType(databaseType);
    }

    /**
     * 解析 MyBatis Mapper XML 使用的 databaseId。
     *
     * @return databaseId
     */
    private String resolveDatabaseId() {
        String databaseType = StringUtils.defaultIfBlank(hotReloadProperties.getDatabaseType(), "MYSQL")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("-", "_");
        if ("POSTGRESQL".equals(databaseType) || "POSTGRES".equals(databaseType)) {
            return "postgresql";
        }
        if ("ORACLE".equals(databaseType) || "ORACLE_12C".equals(databaseType)) {
            return "oracle";
        }
        if ("SQLSERVER".equals(databaseType) || "MSSQL".equals(databaseType) || "SQL_SERVER".equals(databaseType)) {
            return "sqlserver";
        }
        return "mysql";
    }

    /**
     * 审计字段自动填充处理器。
     * <p>
     * 插入和更新实体时自动维护 createdBy、createdTime、updatedBy、updatedTime。
     */
    @Component
    public static class AuditMetaObjectHandler implements MetaObjectHandler {

        /**
         * 插入记录时填充创建和更新时间。
         *
         * @param metaObject MyBatis Plus 元对象
         */
        @Override
        public void insertFill(MetaObject metaObject) {
            Date now = new Date();
            strictInsertFill(metaObject, "createdBy", String.class, "system");
            strictInsertFill(metaObject, "createdTime", Date.class, now);
            strictInsertFill(metaObject, "updatedBy", String.class, "system");
            strictInsertFill(metaObject, "updatedTime", Date.class, now);
        }

        /**
         * 更新记录时刷新更新人和更新时间。
         *
         * @param metaObject MyBatis Plus 元对象
         */
        @Override
        public void updateFill(MetaObject metaObject) {
            strictUpdateFill(metaObject, "updatedBy", String.class, "system");
            strictUpdateFill(metaObject, "updatedTime", Date.class, new Date());
        }
    }
}
