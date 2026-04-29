package cn.lysoy.agentlangservermvp.integration.dto;

/**
 * 语义检索命中条目（Milvus + Neo4j 合并结果可复用）。
 */
public record KnowledgeSearchHit(
        String memoryId,
        String content,
        Double score,
        String memoryType,
        Long timestamp
) {
}
