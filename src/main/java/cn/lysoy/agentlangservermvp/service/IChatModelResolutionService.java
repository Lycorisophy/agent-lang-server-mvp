package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;

/**
 * 解析本次对话使用的模型（可选 modelId / modelCode，缺省则选默认启用模型）。
 */
public interface IChatModelResolutionService {

    /**
     * 解析模型配置；显式指定的模型必须存在且处于启用状态。
     */
    ModelRegistry resolve(Long modelId, String modelCode);
}
