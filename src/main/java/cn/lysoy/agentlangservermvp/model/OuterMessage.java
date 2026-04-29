package cn.lysoy.agentlangservermvp.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外表消息：用户界面展示内容，与 {@link InnerMessage} 分离存储。
 */
@Data
@TableName("outer_message")
public class OuterMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID。 */
    private String sessionId;

    /** 角色：{@code user} / {@code assistant}。 */
    private String role;

    /** 用户可见正文。 */
    private String content;

    /** 内容字符数（Java {@link String#length()}）。 */
    private Integer contentLength;

    private Integer delFlag;
    private String createBy;
    private String updateBy;
    private LocalDateTime updateAt;

    /** 创建时间，毫秒精度，用于稳定排序。 */
    private LocalDateTime createAt;
}
