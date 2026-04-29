package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;

/**
 * 解析向量嵌入模型。
 */
public interface IEmbeddingModelResolutionService {

    ModelRegistry resolve(String modelCode);
}
