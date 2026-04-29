package cn.lysoy.agentlangservermvp.config;

import cn.lysoy.agentlangservermvp.config.properties.KnowledgeStoreProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用知识库外部存储配置绑定（Milvus + Neo4j）。
 */
@Configuration
@EnableConfigurationProperties(KnowledgeStoreProperties.class)
public class KnowledgeVectorGraphConfiguration {
}
