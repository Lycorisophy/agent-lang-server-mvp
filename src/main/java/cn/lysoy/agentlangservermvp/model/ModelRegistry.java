package cn.lysoy.agentlangservermvp.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 大模型注册表实体，对应表 {@code model_registry}。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("model_registry")
public class ModelRegistry extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelCode;
    private String provider;
    private String modelName;
    private String apiKey;
    private String baseUrl;
    private Boolean isActive;

    /** 是否可用于对话。 */
    private Boolean isChat;

    /** 是否可用于压缩场景（如摘要）。 */
    private Boolean isCompression;

    /** 是否可用于智能体（工具调用等）。 */
    private Boolean isAgent;

    /** 是否支持多模态输入。 */
    private Boolean isMultimodal;
}