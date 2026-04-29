package cn.lysoy.agentlangservermvp.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内表消息：实际参与大模型上下文拼装的内容（三期可在此表做压缩摘要）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("inner_message")
public class InnerMessage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    /** 角色：user / assistant / system（压缩摘要等）。 */
    private String role;

    /** 送入模型的文本。 */
    private String content;

    private Integer contentLength;

    /** 压缩前原始长度，非压缩消息为空。 */
    private Integer compressedLength;

    /** 压缩方式：summary、truncation 等。 */
    private String compressMethod;

    /** Token 估算，用于配额与策略。 */
    private Integer tokenCount;
}
