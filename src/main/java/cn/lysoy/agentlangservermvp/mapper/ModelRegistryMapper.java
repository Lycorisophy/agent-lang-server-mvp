package cn.lysoy.agentlangservermvp.mapper;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * {@link ModelRegistry} 的 MyBatis-Plus 映射；扩展按业务定制的 SQL。
 */
@Mapper
public interface ModelRegistryMapper extends BaseMapper<ModelRegistry> {
    /**
     * 查询当前所有启用中的模型记录。
     *
     * @return 启用模型列表
     */
    @Select("SELECT * FROM model_registry WHERE is_active = TRUE")
    List<ModelRegistry> selectAllActive();
}