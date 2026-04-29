package cn.lysoy.agentlangservermvp.knowledge.mapper;

import cn.lysoy.agentlangservermvp.knowledge.model.PermanentMemory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link PermanentMemory} 持久化映射。
 */
@Mapper
public interface PermanentMemoryMapper extends BaseMapper<PermanentMemory> {
}
