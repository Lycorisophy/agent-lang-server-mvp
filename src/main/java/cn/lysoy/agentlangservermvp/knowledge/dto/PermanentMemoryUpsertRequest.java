package cn.lysoy.agentlangservermvp.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 永驻记忆新增/更新请求。
 */
public record PermanentMemoryUpsertRequest(
        Long id,
        String userId,
        @NotBlank(message = "content 不能为空")
        String content
) {
}
