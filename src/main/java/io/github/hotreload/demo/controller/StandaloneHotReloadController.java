package io.github.hotreload.demo.controller;

import io.github.hotreload.demo.config.reload.HotReloadAccessGuard;
import io.github.hotreload.demo.core.recovery.HotReloadRecoveryFileStore;
import io.github.hotreload.demo.core.runtime.ArthasClassReloadExecutor;
import io.github.hotreload.demo.core.runtime.HotReloadRuntimeExecutor;
import io.github.hotreload.demo.util.HotReloadUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.Map;

/**
 * 单机热重载示例接口。
 * <p>
 * 该控制器不依赖 Redis 广播和数据库任务表，适合学习 Arthas 热重载的最小调用链。
 */
@Slf4j
@Api(tags = "单机热重载")
@RestController
@RequestMapping("/standaloneHotReload")
public class StandaloneHotReloadController {

    private final HotReloadRuntimeExecutor hotReloadRuntimeExecutor;
    private final HotReloadAccessGuard hotReloadAccessGuard;
    private final ArthasClassReloadExecutor arthasClassReloadExecutor;
    private final HotReloadRecoveryFileStore recoverFileStore;

    /**
     * 构造单机热重载接口。
     *
     * @param hotReloadRuntimeExecutor 运行时热重载执行器
     * @param hotReloadAccessGuard     简单密钥访问校验器
     * @param arthasClassReloadExecutor Arthas 命令执行器
     * @param recoverFileStore         恢复文件存储组件
     */
    public StandaloneHotReloadController(HotReloadRuntimeExecutor hotReloadRuntimeExecutor,
                                         HotReloadAccessGuard hotReloadAccessGuard,
                                         ArthasClassReloadExecutor arthasClassReloadExecutor,
                                         HotReloadRecoveryFileStore recoverFileStore) {
        this.hotReloadRuntimeExecutor = hotReloadRuntimeExecutor;
        this.hotReloadAccessGuard = hotReloadAccessGuard;
        this.arthasClassReloadExecutor = arthasClassReloadExecutor;
        this.recoverFileStore = recoverFileStore;
    }

    /**
     * 上传 class 或 MyBatis XML 文件并在当前服务实例内直接执行热重载。
     *
     * @param request  HTTP 请求，Header 中需要携带热重载密钥
     * @param file     上传的 class 或 XML 文件
     * @param type     可选热重载类型，未传时自动识别
     * @param beanName Spring Bean 热重载时可选的 BeanName
     * @return 热重载执行结果
     */
    @ApiOperation("单机执行热重载")
    @PostMapping("/hot-reload/all")
    public String hotReload(HttpServletRequest request,
                            @ApiParam(value = "上传的 .class 或 .xml 文件", required = true)
                            @RequestParam("file") MultipartFile file,
                            @ApiParam("热重载类型，支持 SPRING_BEAN、COMMON_CLASS、MYBATIS_XML")
                            @RequestParam(value = "type", required = false) String type,
                            @ApiParam("Spring Bean 名称，未传时按 class 文件自动匹配")
                            @RequestParam(value = "beanName", required = false) String beanName) {
        if (!hotReloadAccessGuard.check(request)) {
            return "密钥错误";
        }
        try {
            byte[] fileBytes;
            try (InputStream inputStream = file.getInputStream()) {
                fileBytes = HotReloadUtils.readBytes(inputStream);
            }
            String fileName = StringUtils.trim(file.getOriginalFilename());
            if (StringUtils.isBlank(fileName)) {
                fileName = "unknown_" + System.currentTimeMillis();
            }
            String actualType = StringUtils.isBlank(type) ? autoDetectFileType(fileBytes, fileName) : type.toUpperCase();
            validateUploadFileName(fileName);
            if ("SPRING_BEAN".equals(actualType)) {
                return reloadSpringBean(beanName, fileBytes, fileName);
            }
            if ("COMMON_CLASS".equals(actualType)) {
                return reloadCommonClass(fileBytes);
            }
            if ("MYBATIS_XML".equals(actualType)) {
                return reloadMyBatisXml(fileBytes);
            }
            return "不支持的类型，请指定 SPRING_BEAN、COMMON_CLASS 或 MYBATIS_XML";
        } catch (Exception e) {
            log.error("单机热重载失败", e);
            return "热重载失败：" + e.getMessage();
        }
    }

    /**
     * 直接调用 Arthas HTTP API 执行命令。
     *
     * @param commandMap 命令请求，command 字段保存 Arthas 命令文本
     * @return Arthas 返回结果
     * @throws Exception Arthas 调用失败时抛出
     */
    @ApiOperation("调用 Arthas 命令")
    @PostMapping("/hot-reload/command")
    public String callCommand(@RequestBody Map<String, String> commandMap) throws Exception {
        String command = commandMap.get("command");
        if (StringUtils.isBlank(command)) {
            throw new IllegalArgumentException("命令参数 command 不能为空");
        }
        return arthasClassReloadExecutor.callArthasApi(command);
    }

    /**
     * 执行 Spring Bean class 热重载并写入恢复文件。
     *
     * @param beanName  页面传入的 BeanName，可为空
     * @param fileBytes class 文件内容
     * @param fileName  上传文件名
     * @return 热重载执行结果
     * @throws Exception 热重载或恢复文件写入失败时抛出
     */
    private String reloadSpringBean(String beanName, byte[] fileBytes, String fileName) throws Exception {
        String resolvedBeanName = hotReloadRuntimeExecutor.resolveSpringBeanName(beanName, fileBytes);
        String reloadResult = hotReloadRuntimeExecutor.reloadSpringBeanRuntime(resolvedBeanName, fileBytes, fileName);
        recoverFileStore.syncSpringBeanRecoverFile(fileName, resolvedBeanName, fileBytes, true);
        return reloadResult + "，persistOnRestart=true";
    }

    /**
     * 执行普通 Java class 热重载并写入恢复文件。
     *
     * @param fileBytes class 文件内容
     * @return 热重载执行结果
     * @throws Exception 热重载或恢复文件写入失败时抛出
     */
    private String reloadCommonClass(byte[] fileBytes) throws Exception {
        String reloadResult = hotReloadRuntimeExecutor.reloadCommonClassRuntime(fileBytes);
        recoverFileStore.syncCommonClassRecoverFile(fileBytes, true);
        return reloadResult + "，persistOnRestart=true";
    }

    /**
     * 执行 MyBatis Mapper XML 热重载并写入恢复文件。
     *
     * @param fileBytes XML 文件内容
     * @return 热重载执行结果
     * @throws Exception XML 解析、MyBatis 刷新或恢复文件写入失败时抛出
     */
    private String reloadMyBatisXml(byte[] fileBytes) throws Exception {
        String reloadResult = hotReloadRuntimeExecutor.reloadMyBatisXmlRuntime(fileBytes);
        recoverFileStore.syncMyBatisXmlRecoverFile(fileBytes, true);
        return reloadResult + "，persistOnRestart=true";
    }

    /**
     * 校验上传文件后缀是否在热重载支持范围内。
     *
     * @param fileName 上传文件名
     */
    private void validateUploadFileName(String fileName) {
        if (StringUtils.isBlank(fileName)
                || !(StringUtils.endsWithIgnoreCase(fileName, ".class")
                || StringUtils.endsWithIgnoreCase(fileName, ".xml"))) {
            throw new IllegalArgumentException("热重载文件只允许上传 .class 或 .xml 文件：" + fileName);
        }
    }

    /**
     * 根据文件内容自动识别热重载类型。
     *
     * @param fileBytes 上传文件内容
     * @param fileName  上传文件名
     * @return 热重载类型
     */
    private String autoDetectFileType(byte[] fileBytes, String fileName) {
        if (isClassFile(fileBytes)) {
            return detectClassType(fileBytes);
        }
        if (isMyBatisXml(fileBytes)) {
            return "MYBATIS_XML";
        }
        if (StringUtils.endsWithIgnoreCase(fileName, ".xml")) {
            throw new IllegalArgumentException("XML 文件内容不含 <mapper namespace，非 MyBatis Mapper 文件");
        }
        throw new IllegalArgumentException("无法自动识别文件类型，请手动指定 type 参数");
    }

    /**
     * 判断文件内容是否为 JVM class 文件。
     *
     * @param fileBytes 上传文件内容
     * @return true 表示文件头为 CAFEBABE
     */
    private boolean isClassFile(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 4) {
            return false;
        }
        int magic = ((fileBytes[0] & 0xFF) << 24)
                | ((fileBytes[1] & 0xFF) << 16)
                | ((fileBytes[2] & 0xFF) << 8)
                | (fileBytes[3] & 0xFF);
        return magic == 0xCAFEBABE;
    }

    /**
     * 判断 XML 文件是否为 MyBatis Mapper 文件。
     *
     * @param fileBytes 上传文件内容
     * @return true 表示文件内容包含 mapper 根节点和 namespace
     */
    private boolean isMyBatisXml(byte[] fileBytes) {
        for (String charset : new String[]{"UTF-8", "GBK"}) {
            try {
                String content = new String(fileBytes, charset);
                if (content.contains("<mapper") && content.contains("namespace=\"")) {
                    return true;
                }
            } catch (Exception ignored) {
                // 尝试下一种常见编码。
            }
        }
        return false;
    }

    /**
     * 根据 class 文件解析类名，并判断该类是否已经由 Spring 容器管理。
     *
     * @param fileBytes class 文件内容
     * @return SPRING_BEAN 或 COMMON_CLASS
     */
    private String detectClassType(byte[] fileBytes) {
        String className = hotReloadRuntimeExecutor.parseClassNameFromBytes(fileBytes);
        boolean springBean = hotReloadRuntimeExecutor.isSpringBeanInContainer(className);
        log.info("解析类名：{}，是否为 Spring Bean：{}", className, springBean);
        return springBean ? "SPRING_BEAN" : "COMMON_CLASS";
    }
}
