package io.github.hotreload.demo.test;

/**
 * Spring Bean 热重载测试服务接口。
 * <p>
 * 修改实现类方法体并重新编译 class 后，可以通过测试接口观察热重载效果。
 */
public interface TestHotReloadService {

    /**
     * 返回测试用户信息。
     *
     * @param userId 测试用户 ID
     * @return 测试返回内容
     */
    String getTestInfo(Long userId);
}
