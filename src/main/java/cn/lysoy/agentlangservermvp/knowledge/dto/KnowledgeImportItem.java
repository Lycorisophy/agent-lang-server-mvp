package cn.lysoy.agentlangservermvp.knowledge.dto;

import java.time.LocalDateTime;

/**
 * 导入记录视图。
 */
public record KnowledgeImportItem(
        String importId,
        String fileName,
        Integer chunkCount,
        String status,
        LocalDateTime createdAt
) {
}
