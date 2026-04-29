package cn.lysoy.agentlangservermvp.mapper;

import cn.lysoy.agentlangservermvp.model.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link ChatSession} 持久化映射。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
