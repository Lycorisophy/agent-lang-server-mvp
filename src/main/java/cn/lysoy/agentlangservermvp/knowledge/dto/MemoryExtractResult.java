package cn.lysoy.agentlangservermvp.knowledge.dto;

import java.util.List;

/**
 * 事件/事实抽取结果。
 */
public record MemoryExtractResult(
        boolean success,
        int extractedCount,
        List<String> memoryIds,
        String message
) {
}
