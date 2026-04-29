package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;

/**
 * 解析事件/事实抽取模型。
 */
public interface IExtractionModelResolutionService {

    ModelRegistry resolve(String modelCode);
}
