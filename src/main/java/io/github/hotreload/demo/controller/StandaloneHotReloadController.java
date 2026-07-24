package io.github.hotreload.demo.controller;

import io.github.hotreload.demo.config.reload.HotReloadAccessGuard;
import io.github.hotreload.demo.core.cluster.HotReloadConstants;
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
     * @return 热重载执行结果
     */
    @ApiOperation("单机执行热重载")
    @PostMapping("/hot-reload/all")
    public String hotReload(HttpServletRequest request,
                            @ApiParam(value = "上传的 .class 或 .xml 文件，服务端按后缀识别类型", required = true)
                            @RequestParam("file") MultipartFile file) {
        if (!hotReloadAccessGuard.check(request)) {
            return "密钥错误";
        }
        try {
            byte[] fileBytes;
            try (InputStream inputStream = file.getInputStream()) {
                fileBytes = HotReloadUtils.readBytes(inputStream);
            }
            String fileName = StringUtils.trim(file.getOriginalFilename());
            String actualType = resolveReloadType(fileName);
            validateReloadFile(actualType, fileBytes, fileName);
            if (HotReloadConstants.FILE_TYPE_CLASS.equals(actualType)) {
                return reloadClass(fileBytes, fileName);
            }
            return reloadMyBatisXml(fileBytes);
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
     * 执行 JVM class 热重载并写入恢复文件。
     *
     * @param fileBytes class 文件内容
     * @param fileName  上传文件名
     * @return 热重载执行结果
     * @throws Exception 热重载或恢复文件写入失败时抛出
     */
    private String reloadClass(byte[] fileBytes, String fileName) throws Exception {
        String reloadResult = hotReloadRuntimeExecutor.reloadClassRuntime(fileBytes);
        recoverFileStore.syncClassRecoverFile(fileName, fileBytes, true);
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
     * 根据上传文件后缀识别热重载类型。
     *
     * @param fileName 上传文件名
     * @return CLASS 或 MYBATIS_XML
     */
    private String resolveReloadType(String fileName) {
        if (StringUtils.endsWithIgnoreCase(fileName, ".class")) {
            return HotReloadConstants.FILE_TYPE_CLASS;
        }
        if (StringUtils.endsWithIgnoreCase(fileName, ".xml")) {
            return HotReloadConstants.FILE_TYPE_MYBATIS_XML;
        }
        throw new IllegalArgumentException("热重载文件只允许上传 .class 或 .xml 文件：" + fileName);
    }

    /**
     * 校验文件内容是否与后缀识别出的类型一致。
     *
     * @param fileType  文件后缀对应的热重载类型
     * @param fileBytes 上传文件内容
     * @param fileName  上传文件名
     */
    private void validateReloadFile(String fileType, byte[] fileBytes, String fileName) {
        if (HotReloadConstants.FILE_TYPE_CLASS.equals(fileType) && !isClassFile(fileBytes)) {
            throw new IllegalArgumentException("热重载文件不是有效的 class 文件：" + fileName);
        }
        if (HotReloadConstants.FILE_TYPE_MYBATIS_XML.equals(fileType) && !isMyBatisXml(fileBytes)) {
            throw new IllegalArgumentException("热重载文件不是 MyBatis Mapper XML：" + fileName);
        }
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
}
