package cn.lysoy.agentlangservermvp.model;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用审计与逻辑删除字段基类；业务实体均宜继承本类以保持字段一致。
 * <p>
 * {@code delFlag} 使用 MyBatis-Plus {@link TableLogic}，查询时默认附带未删除条件。
 * </p>
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