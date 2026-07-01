package io.github.hotreload.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.hotreload.demo.entity.SysConfigDetailEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置明细 Mapper。
 * <p>
 * 热重载使用配置明细维护开关和可操作服务列表。
 */
@Mapper
public interface SysConfigDetailMapper extends BaseMapper<SysConfigDetailEntity> {
}
