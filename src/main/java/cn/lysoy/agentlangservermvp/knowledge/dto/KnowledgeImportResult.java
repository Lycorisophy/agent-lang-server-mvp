package cn.lysoy.agentlangservermvp.knowledge.dto;

/**
 * 文件导入结果。
 */
public record KnowledgeImportResult(
        boolean success,
        String importId,
        int chunkCount,
        String message
) {
}
