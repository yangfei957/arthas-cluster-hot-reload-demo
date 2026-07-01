package io.github.hotreload.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.hotreload.demo.entity.HotReloadTaskInstanceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 热重载相关任务实例 Mapper。
 * <p>
 * 负责记录每个目标节点的执行状态、错误信息和执行结果。
 */
@Mapper
public interface HotReloadTaskInstanceMapper extends BaseMapper<HotReloadTaskInstanceEntity> {
}
