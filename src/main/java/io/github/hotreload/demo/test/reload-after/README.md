# 重载后上传文件

本目录只保存演示热重载时要上传的目标文件，不作为 Java 源码包参与编译。

- `TestHotReloadServiceImpl.class`：上传后 `/testHotReload/serviceClass` 返回“重载后”提示。
- `TestHotReloadUtil.class`：上传后 `/testHotReload/utilityClass?value=` 会把空字符串判断为 `true`。
- `TestHotReloadMapper.xml`：上传后 `/testHotReload/myBatisXml` 返回 `mapper-hot-reload-after` 标记。

这些 class 的类全名仍然是 `io.github.hotreload.demo.test.TestHotReloadServiceImpl` 和 `io.github.hotreload.demo.test.TestHotReloadUtil`，只修改方法体，不新增字段或方法。
