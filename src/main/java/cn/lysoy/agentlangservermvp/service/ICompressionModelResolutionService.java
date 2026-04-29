package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;

/**
 * 解析用于异步摘要/压缩的「中模型」注册信息（version0.3）。
 */
public interface ICompressionModelResolutionService {

    /**
     * 按 {@code modelCode} 解析；为空时取最新创建的、启用且标记为压缩可用的模型。
     */
    ModelRegistry resolve(String modelCode);
}
