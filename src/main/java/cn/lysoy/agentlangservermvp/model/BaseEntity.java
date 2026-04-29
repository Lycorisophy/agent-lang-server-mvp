package cn.lysoy.agentlangservermvp.model;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基础审计实体，包含通用字段
 */
@Data
public abstract class BaseEntity {
    /** 逻辑删除 0-否 1-是 */
    @TableLogic
    private Integer delFlag;

    /** 创建人 */
    private String createBy;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateAt;

    /** 创建时间 */
    private LocalDateTime createAt;
}