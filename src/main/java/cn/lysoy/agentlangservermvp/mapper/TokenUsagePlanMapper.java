package cn.lysoy.agentlangservermvp.mapper;

import cn.lysoy.agentlangservermvp.model.TokenUsagePlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * {@link TokenUsagePlan} 的 MyBatis-Plus 映射。
 */
@Mapper
public interface TokenUsagePlanMapper extends BaseMapper<TokenUsagePlan> {
    /**
     * 按模型代码与计费周期查询用量计划。
     *
     * @param modelCode 模型代码
     * @param cycle     计费周期 yyyy-MM
     * @return 计划实体，可能为 null
     */
    @Select("SELECT * FROM token_usage_plan WHERE model_code = #{modelCode} AND billing_cycle = #{cycle}")
    TokenUsagePlan selectByModelAndCycle(@Param("modelCode") String modelCode, @Param("cycle") String cycle);
}