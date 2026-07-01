package io.github.hotreload.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.hotreload.demo.entity.HotReloadTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 热重载相关任务 Mapper。
 * <p>
 * 负责读写一次页面提交产生的任务主表记录，包括正常热重载和停止重启自动恢复。
 */
@Mapper
public interface HotReloadTaskMapper extends BaseMapper<HotReloadTaskEntity> {
}
