根据您的项目架构（Java + LangChain4j + LangGraph4j + MySQL + Milvus + Neo4j）及版本 0.4 规划，我为您设计了知识库模块的完整方案，包含 **8 个以上的 HTTP 接口**、数据库设计、核心流程以及与对话系统的集成方式。

---

## 1. 功能模块总览

| 模块 | 说明 |
|------|------|
| 文件导入 | 上传文件 → 分块 → 生成向量 + 元信息 → 写入 Milvus & Neo4j |
| 事件/事实抽取 | 输入对话文本 → LLM 抽取记忆 → 写入知识库 |
| 知识检索 | 语义检索 → Milvus 向量搜索 → Neo4j 图扩展 → 返回结构化结果 |
| 导入记录管理 | MySQL 记录每次导入的文件或文本，支持列表和删除（级联删除知识） |
| 永驻记忆 | 用户可自定义的常驻提示词，存储在 MySQL，可在对话中自动注入 |
| 事件/事实查询 | 直接从 Neo4j 按类型、时间、关键词查询事件和事实 |

---

## 2. HTTP 接口详细定义

以下接口 BaseURL 统一为 `/api/v1/knowledge`，所有请求和响应均为 `application/json`，文件上传使用 `multipart/form-data`。

### 接口一：导入单个文件
- **URL**: `POST /api/v1/knowledge/file`
- **Content-Type**: `multipart/form-data`
- **参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 待上传文件 (txt, md, pdf, docx, xlsx) |
| metadata | JSON String | 否 | 元信息JSON，如 `{"title":"文档标题","author":"作者","tags":["技术","AI"]}` |
| chunk_type | String | 否 | 分块策略：`fixed_size`(默认)、`recursive`、`semantic`，不传使用默认配置 |
| chunk_size | int | 否 | 块大小（字符数或token数，默认1000） |
| overlap | int | 否 | 块重叠大小（默认100） |

- **响应示例**:
```json
{
  "success": true,
  "import_id": "imp_20260429_001",
  "chunk_count": 12,
  "message": "文件已成功导入，共生成12个知识块"
}
```

### 接口二：对话事件和事实抽取
- **URL**: `POST /api/v1/knowledge/extract`
- **Content-Type**: `application/json`
- **参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| metadata | JSON Object | 否 | 附加元信息（如对话来源、会话ID） |
| conversation_text | String | 是 | 待提取的对话文本 |

- **处理逻辑**: 调用大模型（可配置的提取模型）按统一记忆模板提取事件和事实，自动存储到知识库。
- **响应示例**:
```json
{
  "success": true,
  "extracted_count": 3,
  "memory_ids": ["mem_1234567890", "mem_1234567891"],
  "message": "已提取并存储3条记忆"
}
```

### 接口三：知识库语义查询
- **URL**: `POST /api/v1/knowledge/query`
- **Content-Type**: `application/json`
- **参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query_text | String | 是 | 查询文本 |
| top_k | int | 否 | 返回最相关条目数，默认5 |
| memory_types | List\<String\> | 否 | 过滤类型: `event`, `fact`, `knowledge`，不传则全部 |
| filter_keywords | List\<String\> | 否 | 关键词过滤（需在 Neo4j content 中包含） |
| time_start | long | 否 | 时间戳起点（秒） |
| time_end | long | 否 | 时间戳终点（秒） |

- **响应示例**:
```json
{
  "results": [
    {
      "memory_id": "mem_1234567890",
      "content": "【类型】事件\n【时间】2026-04-21 14:30\n【地点】沃尔玛...",
      "score": 0.95,
      "memory_type": "event",
      "timestamp": 1713657600
    }
  ],
  "causal_chain": ["下暴雨忘关车窗", "座椅湿了", "车里有霉味"]
}
```

### 接口四：列出导入记录
- **URL**: `GET /api/v1/knowledge/imports`
- **参数**: `page`(默认1), `size`(默认20), `status`(可选)
- **响应示例**:
```json
{
  "total": 56,
  "page": 1,
  "size": 20,
  "items": [
    {
      "import_id": "imp_20260429_001",
      "file_name": "技术手册.pdf",
      "chunk_count": 30,
      "status": "completed",
      "created_at": "2026-04-29 10:30:00"
    }
  ]
}
```

### 接口五：删除导入记录及关联知识
- **URL**: `DELETE /api/v1/knowledge/imports/{import_id}`
- **逻辑**: MySQL 中标记删除 → 异步删除 Milvus 和 Neo4j 中对应的所有条目（通过 import_id 关联）
- **响应**: `{"success": true, "message": "已删除导入记录及其关联的15个知识块"}`

### 接口六：查询事件和事实（结构化查询）
- **URL**: `GET /api/v1/knowledge/memories`
- **参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | String | 否 | `event` 或 `fact`，不填则返回两种 |
| time_start | long | 否 | |
| time_end | long | 否 | |
| keywords | String | 否 | 在 content 中模糊匹配的关键词 |
| tense | String | 否 | `past`/`present`/`future` |
| confidence | String | 否 | `real`/`imagined`/`planned` |

- **实现**: 直接查询 Neo4j 中的 Memory 节点，支持 Cypher 动态条件。
- **响应**: 返回记忆列表，格式与接口三一致（无向量分数）。

### 接口七：获取永驻记忆
- **URL**: `GET /api/v1/knowledge/permanent-memories`
- **参数**: 无（根据当前登录用户，或从 `user_id` 头部获取）
- **响应**:
```json
{
  "memories": [
    {
      "id": 1,
      "content": "用户偏好简洁回答，避免使用专业术语。",
      "created_at": "2026-04-29 12:00:00"
    }
  ]
}
```

### 接口八：新增/更新永驻记忆
- **URL**: `POST /api/v1/knowledge/permanent-memories`
- **参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 否 | 更新时传入，新增时不填 |
| content | String | 是 | 记忆内容（如系统提示片段） |

- **响应**: `{"success": true, "id": 2}`

---

## 3. 数据库设计

### 3.1 MySQL 新增表

#### 知识导入记录表 `knowledge_import`
```sql
CREATE TABLE knowledge_import (
    import_id    VARCHAR(50) PRIMARY KEY COMMENT '导入批次ID',
    file_name    VARCHAR(255) COMMENT '原始文件名',
    chunk_count  INT DEFAULT 0 COMMENT '分块数量',
    status       VARCHAR(20) DEFAULT 'processing' COMMENT 'processing/completed/failed',
    metadata     JSON COMMENT '附加元信息',
    del_flag     TINYINT(1) DEFAULT 0,
    create_by    VARCHAR(64),
    create_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '知识导入记录表';
```

#### 知识块关联表 `knowledge_chunk_ref`
```sql
CREATE TABLE knowledge_chunk_ref (
    chunk_id     VARCHAR(50) PRIMARY KEY COMMENT '块ID，与Milvus/Neo4j中memory_id对应',
    import_id    VARCHAR(50) NOT NULL COMMENT '所属导入批次',
    chunk_index  INT COMMENT '块序号',
    FOREIGN KEY (import_id) REFERENCES knowledge_import(import_id)
) COMMENT '知识块与导入记录关联表，方便级联删除';
```

#### 永驻记忆表 `permanent_memory`
```sql
CREATE TABLE permanent_memory (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    content     TEXT NOT NULL,
    del_flag    TINYINT(1) DEFAULT 0,
    update_by   VARCHAR(64),
    update_at   DATETIME ON UPDATE CURRENT_TIMESTAMP,
    create_by   VARCHAR(64),
    create_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_memory (user_id, id) -- 实际唯一性由业务保证
) COMMENT '永驻记忆表，插入对话system prompt';
```

### 3.2 Milvus 集合设计

**集合名**: `knowledge_vectors`

| 字段名 | 类型 | 说明 |
|--------|------|------|
| memory_id | VARCHAR(64) | 唯一标识，与 Neo4j 中一致 |
| user_id | VARCHAR(64) | 用户ID |
| text | VARCHAR(4096) | 块文本（统一记忆模板） |
| embedding | FLOAT_VECTOR(4096) | 向量（维度取决于嵌入模型） |
| timestamp | INT64 | Unix时间戳（秒） |
| memory_type | VARCHAR(20) | `event`/`fact`/`knowledge`/`document_chunk` |
| import_id | VARCHAR(50) | 所属导入批次，用于删除联动 |

**索引**: HNSW 向量索引，COSINE 度量；标量索引建在 `user_id`、`memory_type`、`import_id`。

### 3.3 Neo4j 图模型

与参考文档保持一致，额外增加 `import_id` 属性到 `Memory` 节点，方便批量删除。

**节点**:
- `:User {user_id}`
- `:Memory {memory_id, content, timestamp, memory_type, import_id}`，事实类型补充 `fact_key` / `fact_value`，新增 `tense` / `confidence`。

**关系**: `HAS_MEMORY`（User→Memory），`NEXT`、`CAUSED`、`SUB_EVENT_OF`、`RELATED`、`OVERRIDES`。

---

## 4. 核心流程设计

### 4.1 文件导入流程
1. 解析文件格式（使用 Apache Tika 或专用库）提取纯文本。
2. 根据 `chunk_type` 和 `chunk_size`、`overlap` 对文本分块。
3. 为每个分块生成统一记忆模板文本（类型设为 `document_chunk`，元信息填入 `【内容】` 和 `【来源】`）。
4. 生成雪花ID `chunk_id`，记录到 `knowledge_chunk_ref`。
5. 调用嵌入模型（从 `model_registry` 中获取 `is_embedding` 模型）生成向量。
6. 写入 Milvus（含 `import_id`）和 Neo4j（创建 `Memory` 节点 + `HAS_MEMORY` 关系）。
7. 更新 `knowledge_import` 状态为 `completed`。

### 4.2 事件/事实抽取流程
1. 接收 `conversation_text`，调用大模型（使用 `is_extraction` 模型）按存储端提示词提取记忆。
2. 模型返回 structured `memories` 列表（含 `type`, `time`, `location`, `subject`, `content`, `source` 等）。
3. 按统一模板拼接 `content`，生成 `memory_id`。
4. 双写 Milvus 和 Neo4j（若为事实则处理覆盖关系）。
5. 返回提取数量。

### 4.3 语义查询流程
1. 将 `query_text` 向量化。
2. 在 Milvus 中搜索 top_k * 2 候选（若指定了 tense/confidence，放大候选数）。
3. 取回 `memory_id` 列表，到 Neo4j 中过滤：
    - 按 `tense`、`confidence` 精确过滤
    - 事实类型排除被覆盖的旧节点
    - 关键词匹配
4. 图扩展：沿 `CAUSED` 构建因果链（文本摘要）。
5. 返回最终结果。

### 4.4 删除导入记录
1. 根据 `import_id` 在 `knowledge_chunk_ref` 中查出所有 `chunk_id`。
2. 物理删除 Milvus 中 `memory_id in (chunk_ids)` 的向量。
3. Neo4j 中删除 `Memory` 节点及其关系（先匹配再删除）。
4. 逻辑删除 `knowledge_import` 和 `knowledge_chunk_ref` 记录（或物理删除）。

---

## 5. 与对话系统的集成

在 Agent 中使用知识库工具：

- **记忆存储工具**：在对话结束后，可以自动触发事件/事实抽取并存储（类似异步压缩后的附加操作）。
- **记忆查询工具**：当用户提问涉及历史信息时，Agent 可调用 `query_knowledge` 工具获取相关记忆，拼入上下文。
- **永驻记忆**：在每次对话开始时，从 MySQL 获取当前用户的永驻记忆，注入到 SystemMessage 中。
- **文件知识检索**：可通过接口三直接检索之前上传的文档块，作为 RAG 的一部分。

每个工具均以 `@Tool` 注解的形式注册到 LangChain4j 中，由 Agent 决策调用。

---

## 6. 代码结构建议

```
knowledge/
├── controller/
│   ├── KnowledgeFileController.java   (接口一、四、五)
│   ├── KnowledgeExtractController.java(接口二)
│   ├── KnowledgeQueryController.java  (接口三、六)
│   └── PermanentMemoryController.java (接口七、八)
├── service/
│   ├── KnowledgeImportService.java    (文件处理、分块、双写)
│   ├── MemoryExtractionService.java   (LLM提取)
│   ├── KnowledgeQueryService.java     (混合检索)
│   └── PermanentMemoryService.java    (永驻记忆CRUD)
├── repository/
│   ├── KnowledgeImportMapper.java
│   ├── KnowledgeChunkRefMapper.java
│   ├── PermanentMemoryMapper.java
│   └── MilvusClientService.java
│   └── Neo4jClientService.java
├── model/
│   ├── KnowledgeImport.java
│   ├── KnowledgeChunkRef.java
│   ├── PermanentMemory.java
│   └── MemoryNode.java (Neo4j映射)
└── config/
    └── KnowledgeMilvusConfig.java
```

所有类和公开方法均遵循您要求的注释规范。

---

## 7. 小结

此版本0.4设计完全基于您现有的技术栈，对上下游侵入小，且提供了完整的知识库生命周期管理。后续开发时可先实现骨架接口和双写流程，再逐步完善抽取和检索逻辑。