# agent-lang-server-mvp

基于 **Spring Boot 4**、**MyBatis-Plus**、**LangChain4j（OpenAI 兼容客户端）** 的智能体服务端 MVP：一期提供大模型注册与缓存；二期提供 **HTTP 同步对话**、**WebSocket 流式对话（打字机）** 与 **按会话查询外表历史**。

向量库（Milvus）、图数据库（Neo4j）、LangGraph4j 编排可在后续阶段接入。

## 环境要求

- JDK 17+
- MySQL 8（或兼容版本）

## 数据库初始化

所有 **MySQL DDL** 放在 [`docs/sql`](docs/sql) 目录，按顺序执行：

1. [`docs/sql/ddl.sql`](docs/sql/ddl.sql) — `model_registry`、`token_usage_plan` 等一期表  
2. [`docs/sql/V002_session_and_messages.sql`](docs/sql/V002_session_and_messages.sql) — 二期 `session`、`outer_message`、`inner_message`

在 [`src/main/resources/application.yaml`](src/main/resources/application.yaml) 中配置数据源 URL、用户名与密码。

## 运行

```bash
mvn spring-boot:run
```

单元测试使用内存 **H2**（见 `src/test/resources/application.yaml`），无需本机 MySQL。

## 一期：模型管理

- `GET /api/models` — 列出缓存中的模型  
- `GET /api/models/{modelCode}` — 按代码查询  
- `POST /api/models` — 新增  
- `PUT /api/models/{modelCode}` — 更新  
- `DELETE /api/models/{modelCode}` — 删除  
- `POST /api/models/refresh` — 刷新缓存  
- `PUT /api/models/{modelCode}/toggle` — 切换启用状态  

请在 `model_registry` 中至少配置一条 **`is_active = true`** 的记录，供二期在未指定模型时作为默认模型（按 `model_code` 字典序取第一条启用记录）。

## 二期：对话与历史

### HTTP 同步对话

`POST /api/chat/messages`

请求体（JSON）：

| 字段 | 必填 | 说明 |
|------|------|------|
| `prompt` | 是 | 用户提示词 |
| `sessionId` | 否 | 续聊时传入已有会话 UUID |
| `modelId` | 否 | 模型表主键 |
| `modelCode` | 否 | 模型业务代码；与 `modelId` 都省略时使用默认启用模型 |
| `userId` | 否 | 写入会话表的业务用户标识 |

响应 `data`：`sessionId`、`reply`（完整助手文本）、`modelCode`。

### 按会话查询历史（外表）

`GET /api/chat/history?sessionId={uuid}`

返回该会话下全部 **外表** `outer_message` 记录（用户可见），按 `create_at` 升序。

### WebSocket 流式对话

1. 连接：`ws://{host}:{port}/ws/chat`（生产环境请使用 `wss://`）。  
2. 发送**一条或多条**文本帧，JSON 格式示例：

```json
{
  "type": "chat",
  "sessionId": "可选，续传已有会话 UUID",
  "modelId": null,
  "modelCode": "可选",
  "prompt": "你好",
  "userId": "可选"
}
```

3. 服务端推送帧（JSON）：

- **增量**：`{"type":"delta","text":"片段"}` — 多次，用于打字机效果  
- **结束**：`{"type":"complete","sessionId":"...","reply":"完整助手文本"}`  
- **错误**：`{"type":"error","code":"业务码","message":"说明"}`  

内外表说明：**外表**存用户可见内容；**内表**存实际拼入大模型上下文的内容（二期与外表用户/助手正文一致，三期可做压缩摘要）。

## 技术说明

- 大模型调用通过 **OpenAI 兼容 HTTP** 接入：`model_registry.base_url` + `model_name` + `api_key`。  
- 对话写库短事务由 [`ChatWriteFacade`](src/main/java/cn/lysoy/agentlangservermvp/service/ChatWriteFacade.java) 承担，避免长耗时推理占用数据库连接。  
- 表名 `session` 为 MySQL 保留字，实体上使用 `` `session` `` 映射（见 `ChatSession`）。

## 仓库约定（给协作者与 AI）

详见 [`.cursor/rules/agent-lang-server.mdc`](.cursor/rules/agent-lang-server.mdc)。
