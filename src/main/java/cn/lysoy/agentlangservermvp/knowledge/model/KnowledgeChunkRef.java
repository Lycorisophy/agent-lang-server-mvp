package cn.lysoy.agentlangservermvp.knowledge.model;

import cn.lysoy.agentlangservermvp.model.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识块与导入批次关联：用于导入删除时级联清理 Milvus/Neo4j。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("knowledge_chunk_ref")
public class KnowledgeChunkRef extends BaseEntity {

    /** 块主键，与向量/图存储中的 memory_id 对齐。 */
    @TableId(type = IdType.INPUT)
    private String chunkId;

    /** 所属导入批次。 */
    private String importId;

    /** 分块顺序。 */
    private Integer chunkIndex;
}
