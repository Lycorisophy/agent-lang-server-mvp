package cn.lysoy.agentlangservermvp.knowledge.dto;

import java.time.LocalDateTime;

/**
 * 永驻记忆展示结构。
 */
public record PermanentMemoryView(
        Long id,
        String content,
        LocalDateTime createdAt
) {
}
