package cn.lysoy.agentlangservermvp.knowledge.dto;

import java.util.List;

/**
 * 导入记录分页结构。
 */
public record KnowledgeImportPage(
        long total,
        long page,
        long size,
        List<KnowledgeImportItem> items
) {
}
