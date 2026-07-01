package io.github.hotreload.demo.core.recovery;

import com.alibaba.fastjson.JSONObject;
import io.github.hotreload.demo.core.cluster.HotReloadConstants;
import io.github.hotreload.demo.util.HotReloadUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 热重载恢复文件存储组件。
 * <p>
 * 当任务选择“重启后保持热重载”时，该组件把上传原文件和恢复元数据封装为 zip 文件写入本地 hot-reload 目录；
 * 当任务不需要重启恢复时，该组件会删除同名恢复文件，避免旧补丁在下次启动时被误恢复。
 */
@Slf4j
@Component
public class HotReloadRecoveryFileStore {

    private static final String APP_ROOT_PATH = System.getProperty("user.dir");
    private static final String BASE_DIR = APP_ROOT_PATH + File.separator + "hot-reload" + File.separator;
    private static final String BEAN_DIR = BASE_DIR + "spring-bean" + File.separator;
    private static final String COMMON_DIR = BASE_DIR + "common-class" + File.separator;
    private static final String XML_DIR = BASE_DIR + "mybatis-xml" + File.separator;
    private static final String RECOVER_PACKAGE_SUFFIX = ".zip";
    private static final String META_ENTRY_NAME = "meta.json";

    /**
     * 同步 Spring Bean class 恢复文件。
     *
     * @param fileName         上传文件名
     * @param beanName         Spring BeanName
     * @param classBytes       class 文件内容
     * @param persistOnRestart 是否需要重启恢复
     */
    public void syncSpringBeanRecoverFile(String fileName, String beanName, byte[] classBytes,
                                          boolean persistOnRestart) {
        syncSpringBeanRecoverFile(fileName, beanName, classBytes, persistOnRestart, null);
    }

    /**
     * 同步 Spring Bean class 恢复文件和元数据。
     *
     * @param fileName         上传文件名
     * @param beanName         Spring BeanName
     * @param classBytes       class 文件内容
     * @param persistOnRestart 是否需要重启恢复
     * @param recoverMeta      恢复文件元数据
     */
    public void syncSpringBeanRecoverFile(String fileName, String beanName, byte[] classBytes,
                                          boolean persistOnRestart, HotReloadRecoverFileMeta recoverMeta) {
        String originalFileName = resolveSpringBeanRecoverFileName(fileName);
        String recoverFileName = toRecoverPackageFileName(originalFileName);
        HotReloadRecoverFileMeta actualMeta = fillBeanNameMeta(beanName, recoverMeta);
        saveOrDeleteRecoverFile(BEAN_DIR + recoverFileName, originalFileName, classBytes,
                persistOnRestart, actualMeta);
    }

    /**
     * 同步普通 class 恢复文件。
     *
     * @param classBytes       class 文件内容
     * @param persistOnRestart 是否需要重启恢复
     */
    public void syncCommonClassRecoverFile(byte[] classBytes, boolean persistOnRestart) {
        syncCommonClassRecoverFile(classBytes, persistOnRestart, null);
    }

    /**
     * 同步普通 class 恢复文件和元数据。
     *
     * @param classBytes       class 文件内容
     * @param persistOnRestart 是否需要重启恢复
     * @param recoverMeta      恢复文件元数据
     */
    public void syncCommonClassRecoverFile(byte[] classBytes, boolean persistOnRestart,
                                           HotReloadRecoverFileMeta recoverMeta) {
        String className = HotReloadUtils.parseClassName(classBytes);
        String defaultFileName = className.substring(className.lastIndexOf('.') + 1) + ".class";
        String originalFileName = resolveRecoverOriginalFileName(recoverMeta, defaultFileName);
        String recoverFileName = toRecoverPackageFileName(originalFileName);
        saveOrDeleteRecoverFile(COMMON_DIR + recoverFileName, originalFileName, classBytes,
                persistOnRestart, recoverMeta);
    }

    /**
     * 同步 MyBatis XML 恢复文件。
     *
     * @param xmlBytes         XML 文件内容
     * @param persistOnRestart 是否需要重启恢复
     * @throws Exception XML namespace 解析失败时抛出
     */
    public void syncMyBatisXmlRecoverFile(byte[] xmlBytes, boolean persistOnRestart) throws Exception {
        syncMyBatisXmlRecoverFile(xmlBytes, persistOnRestart, null);
    }

    /**
     * 同步 MyBatis XML 恢复文件和元数据。
     *
     * @param xmlBytes         XML 文件内容
     * @param persistOnRestart 是否需要重启恢复
     * @param recoverMeta      恢复文件元数据
     * @throws Exception XML namespace 解析失败时抛出
     */
    public void syncMyBatisXmlRecoverFile(byte[] xmlBytes, boolean persistOnRestart,
                                          HotReloadRecoverFileMeta recoverMeta) throws Exception {
        String namespace = parseMyBatisNamespace(xmlBytes);
        String defaultFileName = namespace.replace('.', '_') + ".xml";
        String originalFileName = resolveRecoverOriginalFileName(recoverMeta, defaultFileName);
        String recoverFileName = toRecoverPackageFileName(originalFileName);
        saveOrDeleteRecoverFile(XML_DIR + recoverFileName, originalFileName, xmlBytes,
                persistOnRestart, recoverMeta);
    }

    /**
     * 列出 Spring Bean class 恢复文件。
     *
     * @return 按文件名排序后的恢复文件列表
     */
    public List<File> listSpringBeanFiles() {
        return listFiles(BEAN_DIR, RECOVER_PACKAGE_SUFFIX);
    }

    /**
     * 列出普通 class 恢复文件。
     *
     * @return 按文件名排序后的恢复文件列表
     */
    public List<File> listCommonClassFiles() {
        return listFiles(COMMON_DIR, RECOVER_PACKAGE_SUFFIX);
    }

    /**
     * 列出 MyBatis XML 恢复文件。
     *
     * @return 按文件名排序后的恢复文件列表
     */
    public List<File> listMyBatisXmlFiles() {
        return listFiles(XML_DIR, RECOVER_PACKAGE_SUFFIX);
    }

    /**
     * 按文件类型删除本地热重载恢复文件，用于停止重启自动恢复。
     * <p>
     * fileType 为空或 * 时删除全部恢复目录文件；指定类型时只删除该类型目录下的 zip 恢复包。
     *
     * @param fileType 停止恢复范围，支持 *、SPRING_BEAN、COMMON_CLASS、MYBATIS_XML
     * @return 删除结果摘要
     */
    public String deleteRecoverFiles(String fileType) {
        String targetType = normalizeStopRecoveryFileType(fileType);
        List<String> deleteResults = new ArrayList<>();
        if (HotReloadConstants.FILE_TYPE_ALL.equals(targetType)
                || HotReloadConstants.FILE_TYPE_SPRING_BEAN.equals(targetType)) {
            deleteResults.add("Spring Bean 恢复文件：" + deleteRecoverDir(BEAN_DIR) + " 个");
        }
        if (HotReloadConstants.FILE_TYPE_ALL.equals(targetType)
                || HotReloadConstants.FILE_TYPE_COMMON_CLASS.equals(targetType)) {
            deleteResults.add("普通 class 恢复文件：" + deleteRecoverDir(COMMON_DIR) + " 个");
        }
        if (HotReloadConstants.FILE_TYPE_ALL.equals(targetType)
                || HotReloadConstants.FILE_TYPE_MYBATIS_XML.equals(targetType)) {
            deleteResults.add("MyBatis XML 恢复文件：" + deleteRecoverDir(XML_DIR) + " 个");
        }
        if (!deleteResults.isEmpty()) {
            deleteResults.set(0,"本地清理 " + deleteResults.get(0));
        }
        return StringUtils.join(deleteResults, "；");
    }

    /**
     * 读取恢复文件内容。
     *
     * @param file 恢复文件
     * @return 文件字节内容
     * @throws Exception 文件读取失败时抛出
     */
    public byte[] readFile(File file) throws Exception {
        return readOriginalFileFromPackage(file);
    }

    /**
     * 读取 zip 恢复包中的元数据。
     *
     * @param recoverFile 恢复文件
     * @return 元数据对象，不存在或读取失败时返回 null
     */
    public HotReloadRecoverFileMeta readRecoverMeta(File recoverFile) {
        try {
            byte[] bytes = readPackageEntry(recoverFile, META_ENTRY_NAME, false);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            return JSONObject.parseObject(json, HotReloadRecoverFileMeta.class);
        } catch (Exception e) {
            log.error("读取热重载恢复元数据失败，file={}", recoverFile.getName(), e);
            return null;
        }
    }

    /**
     * 从 MyBatis Mapper XML 内容中解析 namespace。
     *
     * @param xmlBytes XML 文件内容
     * @return Mapper namespace
     * @throws Exception namespace 不存在时抛出
     */
    public String parseMyBatisNamespace(byte[] xmlBytes) throws Exception {
        String xmlContent = new String(xmlBytes, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("namespace\\s*=\\s*\"([^\"]+)\"").matcher(xmlContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("XML 中未找到 namespace 属性，请确认是否为 MyBatis Mapper 文件");
    }

    /**
     * 根据持久化策略保存或删除恢复文件。
     *
     * @param path             zip 恢复包路径
     * @param originalFileName 上传的原始文件名
     * @param bytes            文件内容
     * @param persistOnRestart 是否需要重启恢复
     * @param recoverMeta      恢复元数据
     */
    private void saveOrDeleteRecoverFile(String path, String originalFileName, byte[] bytes,
                                         boolean persistOnRestart, HotReloadRecoverFileMeta recoverMeta) {
        if (persistOnRestart) {
            saveRecoverPackage(path, originalFileName, bytes, recoverMeta);
            log.info("保存热重载恢复文件成功，file={}，meta={}", path, recoverMeta != null);
            return;
        }
        deleteFile(path);
    }

    /**
     * 解析 Spring Bean 恢复文件名，只保留文件名并校验必须是 class 文件。
     *
     * @param fileName 上传文件名
     * @return 安全的恢复文件名
     */
    private String resolveSpringBeanRecoverFileName(String fileName) {
        String simpleFileName = safeFileName(fileName);
        if (StringUtils.isBlank(simpleFileName)) {
            throw new IllegalArgumentException("Spring Bean 恢复文件名不能为空");
        }
        if (!StringUtils.endsWithIgnoreCase(simpleFileName, ".class")) {
            throw new IllegalArgumentException("Spring Bean 恢复文件必须是 class 文件：" + simpleFileName);
        }
        return simpleFileName;
    }

    /**
     * 把原始文件名转换为 zip 恢复包文件名。
     *
     * @param fileName 原始 class/xml 文件名
     * @return zip 文件名
     */
    private String toRecoverPackageFileName(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName + RECOVER_PACKAGE_SUFFIX;
        }
        return fileName.substring(0, index) + RECOVER_PACKAGE_SUFFIX;
    }

    /**
     * 解析恢复包中使用的原始文件名。
     *
     * @param recoverMeta     恢复元数据
     * @param defaultFileName 默认文件名
     * @return 安全的原始文件名
     */
    private String resolveRecoverOriginalFileName(HotReloadRecoverFileMeta recoverMeta, String defaultFileName) {
        String fileName = recoverMeta == null ? null : recoverMeta.getFileName();
        String originalFileName = safeFileName(StringUtils.defaultIfBlank(fileName, defaultFileName));
        if (StringUtils.isBlank(originalFileName)) {
            throw new IllegalArgumentException("恢复包原始文件名不能为空");
        }
        return originalFileName;
    }

    /**
     * 把 BeanName 补充到恢复元数据中，确保启动恢复时能找到原 Bean。
     *
     * @param beanName    Spring BeanName
     * @param recoverMeta 原始恢复元数据
     * @return 补齐后的恢复元数据
     */
    private HotReloadRecoverFileMeta fillBeanNameMeta(String beanName, HotReloadRecoverFileMeta recoverMeta) {
        HotReloadRecoverFileMeta actualMeta = recoverMeta;
        if (actualMeta == null && StringUtils.isNotBlank(beanName)) {
            actualMeta = new HotReloadRecoverFileMeta();
        }
        if (actualMeta != null && StringUtils.isBlank(actualMeta.getBeanName())) {
            actualMeta.setBeanName(beanName);
        }
        return actualMeta;
    }

    /**
     * 移除上传文件名中的路径，只保留最后一级文件名。
     *
     * @param fileName 上传文件名
     * @return 不含路径的文件名
     */
    private String safeFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return fileName;
        }
        String normalized = fileName.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    /**
     * 按后缀列出目录下的恢复文件。
     *
     * @param dirPath 目录路径
     * @param suffix  文件后缀
     * @return 按文件名排序后的文件列表
     */
    private List<File> listFiles(String dirPath, String suffix) {
        File dir = new File(dirPath);
        File[] files = dir.listFiles(file -> file.isFile() && StringUtils.endsWithIgnoreCase(file.getName(), suffix));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return Arrays.asList(files);
    }

    /**
     * 规范化停止恢复范围。
     *
     * @param fileType 页面传入的文件类型
     * @return 规范化后的文件类型
     */
    private String normalizeStopRecoveryFileType(String fileType) {
        String targetType = StringUtils.defaultIfBlank(StringUtils.trim(fileType), HotReloadConstants.FILE_TYPE_ALL)
                .toUpperCase();
        if (HotReloadConstants.FILE_TYPE_ALL.equals(targetType)
                || HotReloadConstants.FILE_TYPE_SPRING_BEAN.equals(targetType)
                || HotReloadConstants.FILE_TYPE_COMMON_CLASS.equals(targetType)
                || HotReloadConstants.FILE_TYPE_MYBATIS_XML.equals(targetType)) {
            return targetType;
        }
        throw new IllegalArgumentException("不支持的停止恢复文件类型：" + fileType);
    }

    /**
     * 删除指定恢复目录下的 zip 恢复包。
     *
     * @param dirPath 恢复目录
     * @return 删除的恢复包数量
     */
    private int deleteRecoverDir(String dirPath) {
        try {
            File dir = new File(dirPath);
            if (!dir.exists()) {
                return 0;
            }
            File baseDir = new File(BASE_DIR).getCanonicalFile();
            File targetDir = dir.getCanonicalFile();
            assertSubPath(baseDir, targetDir);
            File[] files = targetDir.listFiles(file -> file.isFile()
                    && StringUtils.endsWithIgnoreCase(file.getName(), RECOVER_PACKAGE_SUFFIX));
            if (files == null || files.length == 0) {
                return 0;
            }
            int deletedCount = 0;
            for (File file : files) {
                File targetFile = file.getCanonicalFile();
                assertSubPath(targetDir, targetFile);
                if (Files.deleteIfExists(targetFile.toPath())) {
                    deletedCount++;
                }
            }
            return deletedCount;
        } catch (Exception e) {
            throw new RuntimeException("停止重启自动恢复失败，删除恢复文件异常：" + dirPath, e);
        }
    }

    /**
     * 校验待处理路径必须位于指定父目录下。
     *
     * @param parent 父目录
     * @param child  待处理路径
     */
    private void assertSubPath(File parent, File child) {
        String parentPath = parent.getPath();
        String childPath = child.getPath();
        if (!childPath.equals(parentPath) && !childPath.startsWith(parentPath + File.separator)) {
            throw new IllegalArgumentException("恢复文件路径不在允许目录内：" + childPath);
        }
    }

    /**
     * 保存 zip 恢复包。
     *
     * @param path             文件路径
     * @param originalFileName 上传的原始文件名
     * @param bytes            文件内容
     * @param recoverMeta      恢复元数据
     */
    private void saveRecoverPackage(String path, String originalFileName, byte[] bytes,
                                    HotReloadRecoverFileMeta recoverMeta) {
        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
                if (recoverMeta != null) {
                    zipOutputStream.putNextEntry(new ZipEntry(META_ENTRY_NAME));
                    zipOutputStream.write(JSONObject.toJSONString(recoverMeta).getBytes(StandardCharsets.UTF_8));
                    zipOutputStream.closeEntry();
                }
                zipOutputStream.putNextEntry(new ZipEntry(safeZipEntryName(originalFileName)));
                zipOutputStream.write(bytes);
                zipOutputStream.closeEntry();
            }
        } catch (Exception e) {
            throw new RuntimeException("保存恢复文件失败：" + path, e);
        }
    }

    /**
     * 读取 zip 恢复包中的指定条目。
     *
     * @param file      zip 恢复包
     * @param entryName 条目名称
     * @param required  是否必须存在
     * @return 条目字节
     */
    private byte[] readPackageEntry(File file, String entryName, boolean required) throws Exception {
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                if (required) {
                    throw new IllegalArgumentException("恢复文件缺少条目：" + entryName + "，file=" + file.getName());
                }
                return null;
            }
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                return readAllBytes(inputStream);
            }
        }
    }

    /**
     * 读取 zip 恢复包中除 meta.json 之外的唯一原始文件。
     *
     * @param file zip 恢复包
     * @return 上传原文件字节
     * @throws Exception 读取失败或恢复包结构异常时抛出
     */
    private byte[] readOriginalFileFromPackage(File file) throws Exception {
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipEntry originalEntry = null;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || META_ENTRY_NAME.equals(entry.getName())) {
                    continue;
                }
                if (originalEntry != null) {
                    throw new IllegalArgumentException("恢复包中存在多个原始文件，file=" + file.getName());
                }
                originalEntry = entry;
            }
            if (originalEntry == null) {
                throw new IllegalArgumentException("恢复包中缺少上传原文件，file=" + file.getName());
            }
            try (InputStream inputStream = zipFile.getInputStream(originalEntry)) {
                return readAllBytes(inputStream);
            }
        }
    }

    /**
     * 生成安全的 zip 条目名。
     *
     * @param fileName 上传文件名
     * @return 不包含路径的 zip 条目名
     */
    private String safeZipEntryName(String fileName) {
        String entryName = safeFileName(fileName);
        if (StringUtils.isBlank(entryName)) {
            throw new IllegalArgumentException("zip 条目文件名不能为空");
        }
        if (META_ENTRY_NAME.equals(entryName)) {
            throw new IllegalArgumentException("上传文件名不能是 " + META_ENTRY_NAME);
        }
        return entryName;
    }

    /**
     * 读取输入流全部字节。
     *
     * @param inputStream 输入流
     * @return 完整字节内容
     * @throws Exception 读取失败时抛出
     */
    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    /**
     * 删除恢复文件或恢复元数据。
     *
     * @param path 文件路径
     */
    private void deleteFile(String path) {
        try {
            Files.deleteIfExists(new File(path).toPath());
        } catch (Exception e) {
            throw new RuntimeException("删除恢复文件失败：" + path, e);
        }
    }
}
