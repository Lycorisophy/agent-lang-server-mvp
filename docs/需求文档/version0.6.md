好的，我们调整规划：**v0.6 聚焦常用工具实现与提示词工程**，将降级机制和全链路执行日志推迟到 v0.15。下面先给出 v0.6 的具体落地设计，再列出完整的 0.6→1.0 路线图。

---

## v0.6 设计：Agent 地基 + 核心工具 + 提示词工程

### 1. 总体目标
- 在之前定义的 Agent 引擎框架基础上，让智能体**实际可工作**。
- 实现第一批必需工具：知识库查询、事件/事实提取、事件/事实查询、联网搜索。
- 建立**提示词模板库**，并完成核心场景的提示词工程。
- 工具注册机制正式启用，让后续新增工具只需按规范添加即可被 Agent 发现和调用。

### 2. 工具实现计划

所有工具均实现为 **Java 原生 @Tool 注解方法**（或 SkillHub 技能形式，但一期先用原生保证稳定性）。通过 `ToolRegistry` 注册，并提供给 Agent 图中的 `call_model` 节点绑定。

#### 2.1 知识库查询工具 — `query_knowledge`
- **功能**：根据语义查询文本，从 Milvus + Neo4j 检索相关知识。
- **参数**：
    - `queryText`（必填）：查询描述
    - `topK`（可选，默认5）：返回条数
    - `memoryTypes`（可选）：限定类型，如 `["event","fact","knowledge"]`
- **实现**：调用已完成的 `KnowledgeQueryService.query()`。
- **返回**：记忆列表的 JSON 字符串（或结构化文本），供模型阅读。

#### 2.2 事件/事实提取工具 — `extract_memories`
- **功能**：对一段对话文本进行分析，提取事件和事实，并自动存入知识库。
- **参数**：
    - `conversationText`（必填）
    - `userId`（必填）：当前用户ID
- **实现**：调用大模型（提取专用模型）按统一记忆模板抽取，双写 Milvus 和 Neo4j。
- **返回**：提取数量及简要结果。

#### 2.3 事件/事实查询工具 — `query_events_facts`
- **功能**：按结构化条件（时间、关键词、时态等）直接查询 Neo4j 中的事件和事实，不依赖向量搜索。
- **参数**：
    - `type`：事件/事实/全部
    - `keywords`：关键词（模糊匹配）
    - `timeStart`、`timeEnd`：时间范围
    - `tense`、`confidence`：可选过滤
- **实现**：基于 `InnerMemoryService`（或新写一个 Neo4j 查询服务），生成 Cypher 并返回。
- **返回**：格式化记忆列表。

#### 2.4 DuckDuckGo 联网搜索工具 — `web_search`
- **功能**：使用 DuckDuckGo 进行网络搜索，返回摘要和链接。
- **参数**：
    - `query`（必填）：搜索词
    - `maxResults`（可选，默认5）：结果数
- **实现**：集成 DDG 官方 API 或开源 Java 客户端（例如使用 `duckduckgo-search` 的 Java 封装），注意合规与限速。
- **返回**：标题、URL、摘要的列表。

### 3. 提示词模板管理

#### 3.1 数据库表 `prompt_template`
```sql
CREATE TABLE prompt_template (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code VARCHAR(64) NOT NULL COMMENT '模板唯一标识，如 system_default',
    name          VARCHAR(128) NOT NULL COMMENT '模板名称',
    content       TEXT NOT NULL COMMENT '模板内容，支持占位符如 {{user_name}}',
    variables     JSON COMMENT '预期变量说明，如 [{"name":"user_name","type":"string"}]',
    version       INT DEFAULT 1 COMMENT '版本号',
    is_active     TINYINT(1) DEFAULT 1,
    del_flag      TINYINT(1) DEFAULT 0,
    create_by     VARCHAR(64),
    update_by     VARCHAR(64),
    update_at     DATETIME ON UPDATE CURRENT_TIMESTAMP,
    create_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code_version (template_code, version)
) COMMENT '提示词模板表';
```

#### 3.2 一期预置模板
| 模板代码 | 用途 | 占位符示例 |
|---------|------|----------|
| `system_default` | 默认系统提示词，设角色和通用约束 | `user_name`, `permanent_memories`, `current_time` |
| `context_summary` | 压缩时生成历史摘要的提示词 | `conversation_history` |
| `memory_extraction` | 用于 `extract_memories` 调用大模型的指令 | `current_time`, `user_id` |
| `memory_query_rewrite` | 将用户模糊问题转化为 `query_knowledge` 的查询文本 | `user_question`, `memory_types` |
| `web_search_summary` | 对联网搜索结果整合成回答的提示 | `search_results`, `user_question` |

#### 3.3 提示词工程要点
- 每个模板需经过人工测试，确保输出稳定、格式符合预期（尤其记忆提取模板，必须要求 LLM 返回标准 JSON 结构，便于解析）。
- 使用少量样本（Few-shot）内嵌于模板中提升效果。
- 利用 LangChain4j 的 `PromptTemplate` 接口加载并渲染。
- 模板内容由专门的 `PromptTemplateService` 加载到内存缓存（定时刷新），供 Agent 快速渲染。

### 4. Agent 图中的工具绑定
在 `call_model` 节点中：
1. 从 `AgentContext` 获取当前用户权限等级。
2. 通过 `ToolRegistry.getAvailableTools(ctx)` 获得可用工具描述。
3. 动态构建 `ChatRequest` 时，将工具描述传入模型。
4. 模型返回 `ToolExecutionRequest` 后，在图内 `execute_tools` 节点统一执行，并将结果放回上下文。

### 5. 工具执行日志（简化版）
v0.6 暂时不实现细粒度全链路日志，但为了开发和调试，在工具执行时打印 INFO 日志（方法名、参数、耗时、结果摘要）。后续 v0.15 升级为 `agent_execution_log` 表。

### 6. 与上下文的集成
- 在 `ContextManager.buildContext` 中，自动注入当前用户的永驻记忆（从 MySQL 查询）。
- 如果用户问题涉及历史信息，Agent 可自行决定调用 `query_knowledge` 或 `query_events_facts` 获取记忆，再拼入上下文回答。

---

以上设计确保 v0.6 即可让智能体在对话中真正使用知识库和搜索工具，同时为后续复杂模式打下坚实提示词和工具基础。管理后台可在后续版本补充模板编辑界面，目前可通过数据库直接维护。