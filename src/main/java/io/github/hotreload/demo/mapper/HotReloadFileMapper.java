package io.github.hotreload.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.hotreload.demo.entity.HotReloadFileEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 热重载文件 Mapper。
 * <p>
 * 负责读写上传补丁文件内容和文件元数据。
 */
@Mapper
public interface HotReloadFileMapper extends BaseMapper<HotReloadFileEntity> {
}
