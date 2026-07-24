package io.github.hotreload.demo.core.runtime;

import io.github.hotreload.demo.util.HotReloadUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 热重载运行时执行器。
 * <p>
 * 该组件封装 JVM class 和 MyBatis Mapper XML 的实际重载动作，
 * 上层集群任务和单机接口都通过它执行本机热重载。
 */
@Slf4j
@Component
public class HotReloadRuntimeExecutor {

    private final SqlSessionFactory sqlSessionFactory;
    private final ByteBuddyClassReloadExecutor byteBuddyClassReloadExecutor;
    private final Object xmlReloadLock = new Object();

    /**
     * 构造热重载运行时执行器。
     *
     * @param sqlSessionFactory         MyBatis 会话工厂，用于刷新 Mapper XML
     * @param byteBuddyClassReloadExecutor Byte Buddy Agent class 重载执行器
     */
    public HotReloadRuntimeExecutor(SqlSessionFactory sqlSessionFactory,
                                    ByteBuddyClassReloadExecutor byteBuddyClassReloadExecutor) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.byteBuddyClassReloadExecutor = byteBuddyClassReloadExecutor;
    }

    /**
     * 执行 JVM class 热重载。
     *
     * @param classBytes class 文件内容
     * @return 热重载结果
     * @throws Exception Byte Buddy Agent 执行失败时抛出
     */
    public String reloadClass(byte[] classBytes) throws Exception {
        return reloadClassRuntime(classBytes);
    }

    /**
     * 使用 Byte Buddy Agent redefine 替换 JVM 中已经加载的 class。
     *
     * @param classBytes class 文件内容
     * @return 热重载结果
     * @throws Exception Byte Buddy Agent 执行失败时抛出
     */
    public String reloadClassRuntime(byte[] classBytes) throws Exception {
        if (classBytes == null || classBytes.length == 0) {
            throw new IllegalArgumentException("class 文件字节不能为空");
        }
        String className = HotReloadUtils.parseClassName(classBytes);
        String byteBuddyResult = byteBuddyClassReloadExecutor.reloadClass(classBytes);
        log.info("class 热重载成功，className={}", className);
        return "class 热重载成功，className=" + className + "，byteBuddyResult=" + byteBuddyResult;
    }

    /**
     * 执行 MyBatis Mapper XML 热重载。
     *
     * @param xmlBytes XML 文件内容
     * @return 热重载结果
     * @throws Exception XML 解析或 MyBatis 刷新失败时抛出
     */
    public String reloadMyBatisXml(byte[] xmlBytes) throws Exception {
        return reloadMyBatisXmlRuntime(xmlBytes);
    }

    /**
     * 刷新 MyBatis 中指定 namespace 的 Mapper XML。
     *
     * @param xmlBytes XML 文件内容
     * @return 热重载结果，包含刷新后的 statement 信息
     * @throws Exception XML 解析或 MyBatis 刷新失败时抛出
     */
    public String reloadMyBatisXmlRuntime(byte[] xmlBytes) throws Exception {
        Configuration configuration = sqlSessionFactory.getConfiguration();
        String namespace = parseNamespaceFromXml(xmlBytes);
        String resourceId = "hot-reload:" + namespace;
        String reloadResult = "Mapper 热重载完成，namespace=" + namespace;

        synchronized (xmlReloadLock) {
            clearSingleNamespaceCache(configuration, namespace);
            removeLoadedResource(configuration, resourceId);

            Resource resource = new ByteArrayResource(xmlBytes);
            try (InputStream inputStream = resource.getInputStream()) {
                XMLMapperBuilder builder = new XMLMapperBuilder(
                        inputStream, configuration, resourceId, configuration.getSqlFragments());
                builder.parse();
            }

            try {
                List<String> registered = configuration.getMappedStatementNames().stream()
                        .filter(id -> id.startsWith(namespace + "."))
                        .sorted()
                        .map(id -> formatMappedStatementSql(configuration, id))
                        .collect(Collectors.toList());
                reloadResult = reloadResult + "，statementCount=" + registered.size()
                        + "，statements=" + registered;
            } catch (Exception e) {
                log.warn("Mapper 热重载校验失败，namespace={}，原因={}", namespace, e.getMessage());
            }

            log.info("Mapper 热重载完成，namespace={}", namespace);
            return reloadResult;
        }
    }

    /**
     * 根据完整 statementId 输出 SQL 摘要。
     *
     * @param configuration MyBatis 配置对象
     * @param statementId   完整 statementId
     * @return SQL 摘要
     */
    private String formatMappedStatementSql(Configuration configuration, String statementId) {
        MappedStatement mappedStatement = configuration.getMappedStatement(statementId, false);
        return mappedStatement.getId() + " => "
                + mappedStatement.getBoundSql(new Object()).getSql().replaceAll("\\s+", " ").trim();
    }

    /**
     * 从 Mapper XML 内容中解析 namespace。
     *
     * @param xmlBytes XML 文件内容
     * @return Mapper namespace
     * @throws Exception namespace 不存在时抛出
     */
    private String parseNamespaceFromXml(byte[] xmlBytes) throws Exception {
        String xmlContent = new String(xmlBytes, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("namespace\\s*=\\s*\"([^\"]+)\"").matcher(xmlContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("XML 中未找到 namespace 属性，请确认是否为 MyBatis Mapper 文件");
    }

    /**
     * 清理指定 namespace 在 MyBatis Configuration 中的旧缓存，避免重复注册 statement。
     *
     * @param configuration MyBatis 配置对象
     * @param namespace     Mapper namespace
     * @throws Exception 反射访问失败时抛出
     */
    private void clearSingleNamespaceCache(Configuration configuration, String namespace) throws Exception {
        clearMapField(configuration, "mappedStatements", namespace);
        clearMapField(configuration, "resultMaps", namespace);
        clearMapField(configuration, "parameterMaps", namespace);
        clearMapField(configuration, "sqlFragments", namespace);
        clearMapField(configuration, "caches", namespace);
        clearMapField(configuration, "keyGenerators", namespace);
    }

    /**
     * 移除 MyBatis loadedResources 标记，使同一个虚拟资源可以重新解析。
     *
     * @param configuration MyBatis 配置对象
     * @param resourceId    本次热重载使用的虚拟资源 ID
     * @throws Exception 反射访问失败时抛出
     */
    @SuppressWarnings("unchecked")
    private void removeLoadedResource(Configuration configuration, String resourceId) throws Exception {
        Field field = findField(configuration.getClass(), "loadedResources");
        if (field == null) {
            log.debug("未找到 loadedResources 字段，跳过资源标记清理");
            return;
        }
        field.setAccessible(true);
        Set<String> loadedResources = (Set<String>) field.get(configuration);
        loadedResources.remove(resourceId);
    }

    /**
     * 清理 MyBatis Configuration 中指定 Map 字段的 namespace 相关条目。
     *
     * @param configuration MyBatis 配置对象
     * @param fieldName     要清理的字段名
     * @param namespace     Mapper namespace
     */
    @SuppressWarnings("unchecked")
    private void clearMapField(Configuration configuration, String fieldName, String namespace) {
        try {
            Field field = findField(configuration.getClass(), fieldName);
            if (field == null) {
                return;
            }
            field.setAccessible(true);
            Map<String, Object> map = (Map<String, Object>) field.get(configuration);
            Set<String> toRemove = map.keySet().stream()
                    .filter(id -> id.startsWith(namespace + ".") || id.equals(namespace))
                    .collect(Collectors.toSet());
            toRemove.forEach(map::remove);
        } catch (Exception e) {
            log.error("清理 MyBatis 缓存字段失败，fieldName={}，namespace={}", fieldName, namespace, e);
        }
    }

    /**
     * 沿类继承层级查找指定字段。
     *
     * @param clazz     起始类型
     * @param fieldName 字段名
     * @return 找到的字段，找不到时返回 null
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

}
