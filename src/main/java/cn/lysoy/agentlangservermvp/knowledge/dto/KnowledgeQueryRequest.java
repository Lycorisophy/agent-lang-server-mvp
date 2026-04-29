package cn.lysoy.agentlangservermvp.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 语义查询请求。
 */
public record KnowledgeQueryRequest(
        @NotBlank(message = "queryText 不能为空")
        String queryText,
        Integer topK,
        List<String> memoryTypes,
        List<String> filterKeywords,
        Long timeStart,
        Long timeEnd,
        String userId
) {
}
