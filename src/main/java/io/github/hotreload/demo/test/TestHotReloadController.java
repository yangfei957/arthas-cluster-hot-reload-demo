package io.github.hotreload.demo.test;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 热重载验证接口。
 * <p>
 * 该控制器只用于运行期验证测试 class 或 Mapper XML 热重载是否生效。
 */
@Api(tags = "热重载验证")
@RestController
@RequestMapping("/testHotReload")
public class TestHotReloadController {

    private final TestHotReloadService testHotReloadService;
    private final TestHotReloadMapper testHotReloadMapper;

    /**
     * 构造热重载验证接口。
     *
     * @param testHotReloadService Spring Bean 热重载测试服务
     * @param testHotReloadMapper  MyBatis XML 热重载测试 Mapper
     */
    public TestHotReloadController(TestHotReloadService testHotReloadService,
                                   TestHotReloadMapper testHotReloadMapper) {
        this.testHotReloadService = testHotReloadService;
        this.testHotReloadMapper = testHotReloadMapper;
    }

    /**
     * 调用 Spring Bean 测试服务。
     *
     * @param userId 测试用户 ID
     * @return Spring Bean 方法返回内容
     */
    @ApiOperation("验证 Spring Bean 热重载")
    @PostMapping("/springBean")
    public ResponseEntity<String> springBean(
            @ApiParam(value = "测试用户 ID", example = "1001")
            @RequestParam(value = "userId", required = false, defaultValue = "1001") Long userId) {
        return ResponseEntity.ok(testHotReloadService.getTestInfo(userId));
    }

    /**
     * 调用普通工具类静态方法。
     *
     * @param value 测试字符串
     * @return 工具类方法返回内容
     */
    @ApiOperation("验证普通 class 热重载")
    @PostMapping("/commonClass")
    public ResponseEntity<Map<String, Object>> commonClass(
            @ApiParam(value = "测试字符串", example = "demo")
            @RequestParam(value = "value", required = false) String value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reloadHint", "判断普通 class 是否重载成功请看 empty 字段：value 为空字符串时，热重载前为 false，热重载后为 true");
        result.put("value", value);
        result.put("empty", TestHotReloadUtil.isEmpty(value));
        return ResponseEntity.ok(result);
    }

    /**
     * 调用 MyBatis Mapper XML。
     *
     * @return Mapper XML SQL 查询结果
     */
    @ApiOperation("验证 MyBatis XML 热重载")
    @PostMapping("/myBatisXml")
    public ResponseEntity<Map<String, Object>> myBatisXml() {
        return ResponseEntity.ok(testHotReloadMapper.selectDemoResult());
    }

    /**
     * 一次性调用全部测试对象。
     *
     * @param userId 测试用户 ID
     * @param value  测试字符串
     * @return Spring Bean、普通 class 和 MyBatis XML 的当前执行结果
     */
    @ApiOperation("验证全部热重载测试对象")
    @PostMapping("/all")
    public ResponseEntity<Map<String, Object>> all(
            @ApiParam(value = "测试用户 ID", example = "1001")
            @RequestParam(value = "userId", required = false, defaultValue = "1001") Long userId,
            @ApiParam(value = "测试字符串", example = "demo")
            @RequestParam(value = "value", required = false) String value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("springBean", testHotReloadService.getTestInfo(userId));
        result.put("commonClassHint", "判断普通 class 是否重载成功请看 commonClassEmpty 字段：value 为空字符串时，热重载前为 false，热重载后为 true");
        result.put("commonClassValue", value);
        result.put("commonClassEmpty", TestHotReloadUtil.isEmpty(value));
        result.put("myBatisXml", testHotReloadMapper.selectDemoResult());
        return ResponseEntity.ok(result);
    }
}
