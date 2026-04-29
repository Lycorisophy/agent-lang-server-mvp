package cn.lysoy.agentlangservermvp.knowledge.service;

import cn.lysoy.agentlangservermvp.knowledge.dto.MemoryExtractResult;

/**
 * 对话文本事件/事实抽取。
 */
public interface IMemoryExtractionService {

    MemoryExtractResult extract(String conversationText, String metadataJson, String userId);
}
