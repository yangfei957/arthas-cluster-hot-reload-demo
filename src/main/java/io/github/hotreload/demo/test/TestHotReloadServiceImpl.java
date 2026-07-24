package io.github.hotreload.demo.test;

import org.springframework.stereotype.Service;

/**
 * Service 实现类热重载测试实现。
 * <p>
 * 该类由 Spring 容器管理，适合验证 Service 方法体修改后的 Byte Buddy Agent redefine 效果。
 */
@Service
public class TestHotReloadServiceImpl implements TestHotReloadService {

    /**
     * 返回热重载前的测试内容。
     *
     * @param userId 测试用户 ID
     * @return 测试返回内容
     */
    @Override
    public String getTestInfo(Long userId) {
        return "原始代码（热重载前）TestHotReloadService 执行成功，用户ID：" + userId;
    }
}
