package cn.lysoy.agentlangservermvp.mapper;

import cn.lysoy.agentlangservermvp.model.InnerMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link InnerMessage} 持久化映射。
 */
@Mapper
public interface InnerMessageMapper extends BaseMapper<InnerMessage> {
}
