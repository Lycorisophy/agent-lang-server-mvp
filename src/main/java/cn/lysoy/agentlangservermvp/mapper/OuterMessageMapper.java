package cn.lysoy.agentlangservermvp.mapper;

import cn.lysoy.agentlangservermvp.model.OuterMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link OuterMessage} 持久化映射。
 */
@Mapper
public interface OuterMessageMapper extends BaseMapper<OuterMessage> {
}
