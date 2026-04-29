/*
 * version0.4 phase1.5: Milvus 集合与索引初始化脚本（REST v2 示例）
 *
 * 使用说明：
 * 1) 将下方 curl 中的 <MILVUS_BASE_URL>、<TOKEN> 按本机环境替换
 * 2) 向量维度 dim 请与 embedding 模型输出维度一致（如 768/1024/1536）
 * 3) collectionName 与应用配置 app.knowledge.milvus.collection-name 保持一致
 *
 * 默认配置映射：
 * - collectionName: knowledge_vectors
 * - 向量字段: embedding（对应 app.knowledge.milvus.anns-field）
 */

-- 1) 创建 Collection（如已存在可先 drop 或跳过）
-- POST /v2/vectordb/collections/create
-- curl -X POST "<MILVUS_BASE_URL>/v2/vectordb/collections/create" \
--   -H "Authorization: Bearer <TOKEN>" \
--   -H "Content-Type: application/json" \
--   -d '{
--     "collectionName": "knowledge_vectors",
--     "schema": {
--       "autoID": false,
--       "enableDynamicField": false,
--       "fields": [
--         {"fieldName": "memory_id",   "dataType": "VarChar",     "isPrimary": true,  "maxLength": 100},
--         {"fieldName": "user_id",     "dataType": "VarChar",     "maxLength": 64},
--         {"fieldName": "text",        "dataType": "VarChar",     "maxLength": 8192},
--         {"fieldName": "embedding",   "dataType": "FloatVector", "dim": 1024},
--         {"fieldName": "timestamp",   "dataType": "Int64"},
--         {"fieldName": "memory_type", "dataType": "VarChar",     "maxLength": 64},
--         {"fieldName": "import_id",   "dataType": "VarChar",     "maxLength": 64}
--       ]
--     }
--   }'

-- 2) 创建向量索引（embedding 字段）
-- 索引参数可按数据规模调整：HNSW / IVF_FLAT / IVF_SQ8 等
-- POST /v2/vectordb/indexes/create
-- curl -X POST "<MILVUS_BASE_URL>/v2/vectordb/indexes/create" \
--   -H "Authorization: Bearer <TOKEN>" \
--   -H "Content-Type: application/json" \
--   -d '{
--     "collectionName": "knowledge_vectors",
--     "indexParams": [
--       {
--         "fieldName": "embedding",
--         "metricType": "COSINE",
--         "indexName": "idx_embedding_hnsw",
--         "indexType": "HNSW",
--         "params": {
--           "M": 16,
--           "efConstruction": 200
--         }
--       }
--     ]
--   }'

-- 3) 加载 Collection（查询前建议执行）
-- POST /v2/vectordb/collections/load
-- curl -X POST "<MILVUS_BASE_URL>/v2/vectordb/collections/load" \
--   -H "Authorization: Bearer <TOKEN>" \
--   -H "Content-Type: application/json" \
--   -d '{
--     "collectionName": "knowledge_vectors"
--   }'

-- 4) 查看 Collection 描述（用于与启动校验对齐）
-- POST /v2/vectordb/collections/describe
-- curl -X POST "<MILVUS_BASE_URL>/v2/vectordb/collections/describe" \
--   -H "Authorization: Bearer <TOKEN>" \
--   -H "Content-Type: application/json" \
--   -d '{
--     "collectionName": "knowledge_vectors"
--   }'
