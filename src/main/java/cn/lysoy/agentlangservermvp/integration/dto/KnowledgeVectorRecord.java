package cn.lysoy.agentlangservermvp.integration.dto;

import java.util.List;

/**
 * 写入 Milvus 的知识向量记录。
 */
public record KnowledgeVectorRecord(
        String memoryId,
        String userId,
        String text,
        List<Double> embedding,
        Long timestamp,
        String memoryType,
        String importId
) {
}
