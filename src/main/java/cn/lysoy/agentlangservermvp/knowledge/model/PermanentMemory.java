package cn.lysoy.agentlangservermvp.knowledge.model;

import cn.lysoy.agentlangservermvp.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 永驻记忆：用户级长期提示词片段，供对话前注入 system 上下文。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("permanent_memory")
public class PermanentMemory extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户。 */
    private String userId;

    /** 永驻提示词文本。 */
    private String content;
}
