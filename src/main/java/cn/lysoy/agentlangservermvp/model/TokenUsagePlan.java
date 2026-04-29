package cn.lysoy.agentlangservermvp.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Token 用量计划实体，对应表 {@code token_usage_plan}。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("token_usage_plan")
public class TokenUsagePlan extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelCode;
    private String userId;
    private Long monthlyLimit;
    private Long usedTokens;
    private String billingCycle;
}