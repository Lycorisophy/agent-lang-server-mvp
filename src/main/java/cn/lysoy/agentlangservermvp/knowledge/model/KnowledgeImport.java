package cn.lysoy.agentlangservermvp.knowledge.model;

import cn.lysoy.agentlangservermvp.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识导入记录：描述单次文件/文本入库任务的状态与统计。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("knowledge_import")
public class KnowledgeImport extends BaseEntity {

    /** 导入批次主键，如 imp_yyyyMMdd_xxx。 */
    @TableId(type = IdType.INPUT)
    private String importId;

    /** 原始文件名（文本导入时可为空或用虚拟名）。 */
    private String fileName;

    /** 本次导入产出的块数量。 */
    private Integer chunkCount;

    /** processing/completed/failed。 */
    private String status;

    /** JSON 字符串元信息（来源、标题、标签等）。 */
    private String metadata;
}
