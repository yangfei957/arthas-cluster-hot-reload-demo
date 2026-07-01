package io.github.hotreload.demo.test;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * MyBatis XML 热重载验证 Mapper。
 * <p>
 * 修改 mapper XML 中的 SQL 后，可以通过测试接口观察 MyBatis XML 热重载是否生效。
 */
@Mapper
public interface TestHotReloadMapper {

    /**
     * 查询测试 SQL 结果。
     *
     * @return SQL 返回的键值结果
     */
    Map<String, Object> selectDemoResult();
}
