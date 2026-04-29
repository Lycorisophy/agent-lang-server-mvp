package cn.lysoy.agentlangservermvp.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体，对应表 {@code session}（MySQL 保留字表名需反引号）。
 * <p>
 * 用于组织多轮对话；与 {@link OuterMessage}、{@link InnerMessage} 通过 {@code session_id} 关联。
 * </p>
 */
@Data
@TableName("`session`")
public class ChatSession {

    /**
     * 会话主键，UUID 字符串。
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 业务侧用户标识，可空。
     */
    private String userId;

    /**
     * 会话标题，可由首条用户消息截断生成。
     */
    private String title;

    /**
     * 逻辑删除：0 未删除，1 已删除。
     */
    private Integer delFlag;

    private String createBy;
    private String updateBy;
    private LocalDateTime updateAt;
    private LocalDateTime createAt;
}
