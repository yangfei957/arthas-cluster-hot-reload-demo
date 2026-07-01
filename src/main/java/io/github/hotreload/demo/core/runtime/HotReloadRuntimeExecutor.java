package io.github.hotreload.demo.core.runtime;

import io.github.hotreload.demo.util.HotReloadUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
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
 * 该组件封装 Spring Bean class、普通 class 和 MyBatis Mapper XML 的实际重载动作，
 * 上层集群任务和单机接口都通过它执行本机热重载。
 */
@Slf4j
@Component
public class HotReloadRuntimeExecutor implements ApplicationContextAware {

    private final SqlSessionFactory sqlSessionFactory;
    private final ArthasClassReloadExecutor arthasClassReloadExecutor;
    private final Object xmlReloadLock = new Object();

    private DefaultListableBeanFactory beanFactory;
    private ApplicationContext applicationContext;

    /**
     * 构造热重载运行时执行器。
     *
     * @param sqlSessionFactory         MyBatis 会话工厂，用于刷新 Mapper XML
     * @param arthasClassReloadExecutor Arthas class 重载执行器
     */
    public HotReloadRuntimeExecutor(SqlSessionFactory sqlSessionFactory,
                                    ArthasClassReloadExecutor arthasClassReloadExecutor) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.arthasClassReloadExecutor = arthasClassReloadExecutor;
    }

    /**
     * 保存 Spring 容器上下文，后续用于判断 Bean 是否存在和校验 Bean 对应的真实类。
     *
     * @param ctx Spring 应用上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        this.beanFactory = (DefaultListableBeanFactory) ctx.getAutowireCapableBeanFactory();
        this.applicationContext = ctx;
    }

    /**
     * 执行 Spring Bean class 热重载。
     *
     * @param beanName   页面传入的 BeanName，可为空
     * @param classBytes class 文件内容
     * @param fileName   上传文件名
     * @return 热重载结果
     * @throws Exception Arthas 执行失败或 Bean 校验失败时抛出
     */
    public String reloadSpringBean(String beanName, byte[] classBytes, String fileName) throws Exception {
        String resolvedBeanName = resolveSpringBeanName(beanName, classBytes);
        return reloadSpringBeanRuntime(resolvedBeanName, classBytes, fileName);
    }

    /**
     * 使用已解析的 BeanName 执行 Spring Bean class 热重载。
     *
     * @param beanName   已确认的 Spring BeanName
     * @param classBytes class 文件内容
     * @param fileName   上传文件名
     * @return 热重载结果
     * @throws Exception Arthas 执行失败或 Bean 校验失败时抛出
     */
    public String reloadSpringBeanRuntime(String beanName, byte[] classBytes, String fileName) throws Exception {
        String newClassName = HotReloadUtils.parseClassName(classBytes);
        String resolvedBeanName = resolveSpringBeanName(beanName, newClassName);
        String arthasResult = arthasClassReloadExecutor.reloadClass(classBytes);
        log.info("Spring Bean 热重载成功，beanName={}，className={}", resolvedBeanName, newClassName);
        return "Spring Bean 热重载成功，beanName=" + resolvedBeanName
                + "，className=" + newClassName
                + "，fileName=" + fileName
                + "，arthasResult=" + arthasResult;
    }

    /**
     * 根据 class 文件内容解析或校验 Spring BeanName。
     *
     * @param beanName   页面传入的 BeanName，可为空
     * @param classBytes class 文件内容
     * @return 容器中匹配的 BeanName
     */
    public String resolveSpringBeanName(String beanName, byte[] classBytes) {
        String className = HotReloadUtils.parseClassName(classBytes);
        return resolveSpringBeanName(beanName, className);
    }

    /**
     * 执行普通 Java class 热重载。
     *
     * @param classBytes class 文件内容
     * @return 热重载结果
     * @throws Exception Arthas 执行失败时抛出
     */
    public String reloadCommonClass(byte[] classBytes) throws Exception {
        return reloadCommonClassRuntime(classBytes);
    }

    /**
     * 执行普通 Java class 热重载，不进行 Spring BeanName 校验。
     *
     * @param classBytes class 文件内容
     * @return 热重载结果
     * @throws Exception Arthas 执行失败时抛出
     */
    public String reloadCommonClassRuntime(byte[] classBytes) throws Exception {
        if (classBytes == null || classBytes.length == 0) {
            throw new IllegalArgumentException("class 文件字节不能为空");
        }
        String className = HotReloadUtils.parseClassName(classBytes);
        ensureClassLoaded(className);
        String arthasResult = arthasClassReloadExecutor.reloadClass(classBytes);
        log.info("普通类热重载成功，className={}", className);
        return "普通类热重载成功，className=" + className + "，arthasResult=" + arthasResult;
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
     * 判断指定类是否已经由 Spring 容器管理。
     *
     * @param fullClassName class 文件解析出的完整类名
     * @return true 表示该类对应 Spring Bean
     */
    public boolean isSpringBeanInContainer(String fullClassName) {
        try {
            String[] beanNames = beanFactory.getBeanDefinitionNames();
            for (String beanName : beanNames) {
                if (isBeanMatchClass(beanName, fullClassName)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 class 文件字节码中解析完整类名。
     *
     * @param classBytes class 文件内容
     * @return 完整类名
     */
    public String parseClassNameFromBytes(byte[] classBytes) {
        return HotReloadUtils.parseClassName(classBytes);
    }

    /**
     * 解析并校验 Spring BeanName。
     *
     * @param beanName     页面传入的 BeanName，可为空
     * @param newClassName class 文件解析出的完整类名
     * @return 容器中匹配的 BeanName
     */
    private String resolveSpringBeanName(String beanName, String newClassName) {
        String resolvedBeanName = beanName;
        if (StringUtils.isBlank(resolvedBeanName)) {
            resolvedBeanName = inferBeanName(newClassName);
            log.info("自动推断 BeanName：{}", resolvedBeanName);
        }

        String registeredClassName = null;
        try {
            registeredClassName = beanFactory.getBeanDefinition(resolvedBeanName).getBeanClassName();
        } catch (Exception ignored) {
        }

        if (registeredClassName == null) {
            Class<?> type = applicationContext.getType(resolvedBeanName);
            if (type == null) {
                throw new RuntimeException("Bean 不存在：" + resolvedBeanName + "，请检查 beanName 是否正确");
            }
            registeredClassName = unwrapProxyClassName(type.getName());
        }

        if (!registeredClassName.equals(newClassName)) {
            throw new IllegalArgumentException("类不匹配，容器中 [" + resolvedBeanName + "] 对应类："
                    + registeredClassName + "，上传类：" + newClassName);
        }
        return resolvedBeanName;
    }

    /**
     * 根据类名在 Spring 容器中查找匹配的 BeanName，找不到时退回到默认首字母小写规则。
     *
     * @param fullClassName 完整类名
     * @return 推断出的 BeanName
     */
    private String inferBeanName(String fullClassName) {
        try {
            String[] beanNames = beanFactory.getBeanDefinitionNames();
            for (String beanName : beanNames) {
                if (isBeanMatchClass(beanName, fullClassName)) {
                    return beanName;
                }
            }
        } catch (Exception ignored) {
        }
        String simpleName = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * 判断指定 Bean 是否对应上传 class 文件解析出的类名。
     *
     * @param beanName      Spring BeanName
     * @param fullClassName 完整类名
     * @return true 表示 Bean 与 class 文件匹配
     */
    private boolean isBeanMatchClass(String beanName, String fullClassName) {
        try {
            String beanClassName = beanFactory.getBeanDefinition(beanName).getBeanClassName();
            if (fullClassName.equals(beanClassName)) {
                return true;
            }
            Class<?> type = beanFactory.getType(beanName);
            if (type != null) {
                return fullClassName.equals(type.getName())
                        || fullClassName.equals(unwrapProxyClassName(type.getName()));
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 去掉 Spring CGLIB 代理类名后缀，得到用户代码中的原始类名。
     *
     * @param typeName Spring 返回的类型名称
     * @return 去代理后的类名
     */
    private String unwrapProxyClassName(String typeName) {
        return typeName.contains("$$") ? typeName.substring(0, typeName.indexOf("$$")) : typeName;
    }

    /**
     * 尝试主动加载目标类，提前暴露普通 class 不在当前进程中的风险。
     *
     * @param className 完整类名
     */
    private void ensureClassLoaded(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            log.warn("类尚未加载或不在 classpath 中，className={}，Arthas retransform 可能失败", className);
        }
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
