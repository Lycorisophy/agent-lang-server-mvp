package cn.lysoy.agentlangservermvp.knowledge.dto;

import cn.lysoy.agentlangservermvp.integration.dto.KnowledgeSearchHit;

import java.util.List;

/**
 * 语义查询返回。
 */
public record KnowledgeQueryResult(
        List<KnowledgeSearchHit> results,
        List<String> causalChain
) {
}
