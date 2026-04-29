package cn.lysoy.agentlangservermvp.mapper;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link ModelRegistry} 的 MyBatis-Plus 映射。
 * <p>
 * 自定义查询请使用 {@link com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper}
 * 或 XML 中<b>逐列写出</b>字段，禁止手写 {@code SELECT *}。
 * </p>
 */
@Mapper
public interface ModelRegistryMapper extends BaseMapper<ModelRegistry> {
}
