package cn.lysoy.agentlangservermvp.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 对话抽取请求。
 */
public record KnowledgeExtractRequest(
        String metadata,
        @NotBlank(message = "conversationText 不能为空")
        String conversationText,
        String userId
) {
}
