package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;

/**
 * 智能体对话所用模型解析（工具调用等）——占位，后续与 Agent 流水线对接。
 */
public interface IAgentModelResolutionService {

    /**
     * 预留入口；当前实现抛出业务异常，避免误用。
     */
    ModelRegistry resolve(Long modelId, String modelCode);
}
