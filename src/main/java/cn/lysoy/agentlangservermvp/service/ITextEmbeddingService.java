package cn.lysoy.agentlangservermvp.service;

import java.util.List;

/**
 * 文本向量化服务：对接可配置的 OpenAI 兼容 embedding 接口。
 */
public interface ITextEmbeddingService {

    /**
     * 将输入文本向量化。
     *
     * @param text      待向量化文本
     * @param modelCode 可选模型编码；为空时自动选择默认可用 embedding 模型
     * @return embedding 向量
     */
    List<Double> embed(String text, String modelCode);
}
