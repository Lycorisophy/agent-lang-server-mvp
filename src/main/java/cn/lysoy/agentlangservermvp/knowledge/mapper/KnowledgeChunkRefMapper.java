package cn.lysoy.agentlangservermvp.knowledge.mapper;

import cn.lysoy.agentlangservermvp.knowledge.model.KnowledgeChunkRef;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link KnowledgeChunkRef} 持久化映射。
 */
@Mapper
public interface KnowledgeChunkRefMapper extends BaseMapper<KnowledgeChunkRef> {
}
