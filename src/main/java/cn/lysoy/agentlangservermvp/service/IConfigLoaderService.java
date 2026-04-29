package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;

import java.util.List;

/**
 * 模型配置缓存：启动加载、定时刷新、按代码读取与全量列表。
 */
public interface IConfigLoaderService {

    /**
     * 从数据库全量刷新本地缓存（覆盖式）。
     */
    void refreshModels();

    /**
     * 根据模型代码从缓存读取配置。
     *
     * @param modelCode 模型代码
     * @return 配置实体，缺失时为 null
     */
    ModelRegistry getModelConfig(String modelCode);

    /**
     * 返回当前缓存中所有模型快照（不可变列表）。
     */
    List<ModelRegistry> getAllModels();
}
