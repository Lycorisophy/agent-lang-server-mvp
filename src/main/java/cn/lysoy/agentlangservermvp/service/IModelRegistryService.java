package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.model.ModelRegistry;

import java.util.List;

/**
 * 模型注册业务：增删改查、缓存刷新、启用切换。
 */
public interface IModelRegistryService {

    List<ModelRegistry> listModels();

    ModelRegistry getModelByCode(String modelCode);

    ModelRegistry createModel(ModelRegistry model);

    ModelRegistry updateModel(String modelCode, ModelRegistry updatedModel);

    void deleteModel(String modelCode);

    String refreshModelCache();

    String toggleActive(String modelCode);
}
