package io.github.hotreload.demo.core.runtime;

import io.github.hotreload.demo.util.HotReloadUtils;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.springframework.stereotype.Component;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Byte Buddy Agent class 重载执行器。
 * <p>
 * 组件启动时通过 ByteBuddyAgent 安装 Java Agent 并获取 Instrumentation，
 * 热重载时使用上传的 class 字节重新定义 JVM 中已经加载的目标类。
 */
@Slf4j
@Component
public class ByteBuddyClassReloadExecutor {

    private final Instrumentation instrumentation;

    /**
     * 安装 Byte Buddy Agent，并确认当前 JVM 支持类重新定义。
     */
    public ByteBuddyClassReloadExecutor() {
        try {
            this.instrumentation = ByteBuddyAgent.install();
        } catch (Exception e) {
            throw new IllegalStateException("Byte Buddy Agent 安装失败，请确认当前运行环境支持 Attach API", e);
        }
        if (!instrumentation.isRedefineClassesSupported()) {
            throw new IllegalStateException("当前 JVM 不支持 redefineClasses，无法执行 class 热重载");
        }
        log.info("Byte Buddy Agent 已安装，class 重定义能力可用");
    }

    /**
     * 使用 JVM Instrumentation 重新定义已经加载的 class。
     *
     * @param classBytes class 文件完整字节
     * @return class 重载结果
     * @throws Exception 目标类未加载、存在多个 ClassLoader 副本或 JVM 拒绝重定义时抛出
     */
    public String reloadClass(byte[] classBytes) throws Exception {
        String className = HotReloadUtils.parseClassName(classBytes);
        Class<?> targetClass = resolveLoadedClass(className);
        if (!instrumentation.isModifiableClass(targetClass)) {
            throw new IllegalStateException("目标类不允许被 JVM 修改：" + className);
        }

        instrumentation.redefineClasses(new ClassDefinition(targetClass, classBytes));
        String classLoader = describeClassLoader(targetClass.getClassLoader());
        log.info("Byte Buddy Agent class 热重载成功，className={}，classLoader={}", className, classLoader);
        return "Byte Buddy Agent class 热重载成功，className=" + className + "，classLoader=" + classLoader;
    }

    /**
     * 返回当前 Agent 和 JVM class 重定义能力状态。
     *
     * @return Agent 状态摘要
     */
    public String getStatus() {
        return "Byte Buddy Agent 已安装，redefineClassesSupported="
                + instrumentation.isRedefineClassesSupported()
                + "，retransformClassesSupported=" + instrumentation.isRetransformClassesSupported();
    }

    /**
     * 从 JVM 已加载类中查找唯一目标类。
     * <p>
     * 同名类可能由多个 ClassLoader 分别加载。上传文件没有携带 ClassLoader 标识，
     * 此时拒绝自动选择，避免把补丁应用到错误的类副本。
     *
     * @param className 完整类名
     * @return 唯一已加载类
     */
    private Class<?> resolveLoadedClass(String className) {
        List<Class<?>> matchedClasses = new ArrayList<>();
        for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
            if (className.equals(loadedClass.getName())) {
                matchedClasses.add(loadedClass);
            }
        }
        if (matchedClasses.isEmpty()) {
            throw new IllegalStateException("目标类尚未加载到当前 JVM：" + className);
        }
        if (matchedClasses.size() > 1) {
            String classLoaders = matchedClasses.stream()
                    .map(targetClass -> describeClassLoader(targetClass.getClassLoader()))
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("目标类存在多个 ClassLoader 副本，无法安全选择："
                    + className + "，classLoaders=" + classLoaders);
        }
        return matchedClasses.get(0);
    }

    /**
     * 生成人类可读的 ClassLoader 标识。
     *
     * @param classLoader 类加载器，null 表示 Bootstrap ClassLoader
     * @return ClassLoader 类型和实例标识
     */
    private String describeClassLoader(ClassLoader classLoader) {
        if (classLoader == null) {
            return "BootstrapClassLoader";
        }
        return classLoader.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(classLoader));
    }
}