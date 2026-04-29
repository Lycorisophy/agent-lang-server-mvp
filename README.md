# agent-lang-server-mvp

基于 **Spring Boot 4**、**MyBatis-Plus**、**LangChain4j（OpenAI 兼容客户端）** 的智能体服务端 MVP：一期提供大模型注册与缓存；二期提供 **HTTP 同步对话**、**WebSocket 流式对话（打字机）** 与 **按会话查询外表历史**。

向量库（Milvus）、图数据库（Neo4j）、LangGraph4j 编排可在后续阶段接入。

## 环境要求

- JDK 17+
- MySQL 8（或兼容版本）

## 数据库初始化

所有 **MySQL DDL** 放在 [`docs/sql`](docs/sql) 目录，按顺序执行：

1. [`docs/sql/V001_model.sql`](docs/sql/V001_model.sql) — `model_registry`、`token_usage_plan` 等一期表  
2. [`docs/sql/V002_session_and_messages.sql`](docs/sql/V002_session_and_messages.sql) — 二期 `session`、`outer_message`、`inner_message`

默认数据源见 [`application.yaml`](src/main/resources/application.yaml)（占位账号，可按需修改）。

### 本机 MySQL（local 配置，不提交仓库）

1. 复制 [`application-local.example.yaml`](src/main/resources/application-local.example.yaml) 为 **`src/main/resources/application-local.yaml`**，填写本机密码。  
2. 该文件已列入 [`.gitignore`](.gitignore)，避免将密码提交到 Git。  
3. 启动时激活 **`local`**  profile，例如：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

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

1. **连接**：`ws://{host}:{port}/ws/chat`（HTTPS 站点请用 `wss://`）。  
2. **连接成功**：服务端自动推送一帧 `type` 为 `connected`，`message` 中含协议说明（便于联调）。  
3. **心跳**（可选）：客户端发送 `{"type":"ping"}`，服务端回复 `{"type":"pong"}`（其余字段可为 null）。  
4. **发起对话**：发送文本帧 JSON：

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

5. **服务端推送帧**（JSON，`null` 字段通常省略）：

| type | 含义 |
|------|------|
| `connected` | 已连接，见 `message` |
| `pong` | 心跳响应 |
| `delta` | 增量片段，见 `text`，多次 |
| `complete` | 本轮结束，见 `sessionId` 与 `reply` |
| `error` | 失败，见 `code` 与 `message` |

同一会话内发送采用同步锁，避免与流式回调并发写 WebSocket 导致异常。

内外表说明：**外表**存用户可见内容；**内表**存实际拼入大模型上下文的内容（二期与外表用户/助手正文一致，三期可做压缩摘要）。

## 技术说明

- 大模型调用通过 **OpenAI 兼容 HTTP** 接入：`model_registry.base_url` + `model_name` + `api_key`。  
- 对话写库短事务由 [`IChatWriteService`](src/main/java/cn/lysoy/agentlangservermvp/service/IChatWriteService.java) 的实现 [`ChatWriteServiceImpl`](src/main/java/cn/lysoy/agentlangservermvp/service/impl/ChatWriteServiceImpl.java) 承担，避免长耗时推理占用数据库连接。  
- LangChain4j 模型工厂位于 [`integration`](src/main/java/cn/lysoy/agentlangservermvp/integration/LangChainChatModelFactory.java) 包（非 `*Service` 业务接口）。  
- 表名 `session` 为 MySQL 保留字，实体上使用 `` `session` `` 映射（见 `ChatSession`）。

## 工程约定

- **Service 层**：对外能力定义在 `cn.lysoy.agentlangservermvp.service` 下的 `I*` 接口；实现类放在 `service.impl`，使用 `@Service`，Controller 与其它 Bean 只依赖接口。  
- **Mapper**：禁止在注解或 XML 中手写 `SELECT *`；优先使用 MyBatis-Plus `LambdaQueryWrapper` 或 XML 中逐列写出字段。  
- **实体**：持久化实体均继承 [`BaseEntity`](src/main/java/cn/lysoy/agentlangservermvp/model/BaseEntity.java)（含逻辑删除与审计字段），子类使用 `@EqualsAndHashCode(callSuper = true)`。  
- **异步**：全局线程池见 [`AsyncExecutorConfiguration`](src/main/java/cn/lysoy/agentlangservermvp/config/AsyncExecutorConfiguration.java)（`@EnableAsync` + `@Primary` `ThreadPoolTaskExecutor`，bean 名 `applicationTaskExecutor`）；业务方法可用 `@Async` 或手动 `submit`。

## 仓库约定（给协作者与 AI）

详见 [`.cursor/rules/agent-lang-server.mdc`](.cursor/rules/agent-lang-server.mdc)。
