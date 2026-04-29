## v0.7 ReAct 模式完整实现方案（综合版）

基于前序 v0.6 的 Agent 地基（工具注册中心、提示词管理、上下文管理、核心工具），v0.7 将正式实现 ReAct（Reasoning + Acting）循环，并全面集成知识库、搜索、客户端执行等能力。以下为完整设计与落地指南。

---

### 1. 总体架构

```
┌────────────────────── 客户端（浏览器） ──────────────────────┐
│  WebSocket 长连接：接收思考 token、工具状态、最终答案、文件     │
└──────────────────────┬───────────────────────────────────────┘
                       │
┌──────────────────────▼─────────── 服务端 ────────────────────┐
│  Agent 引擎 (LangGraph4j)                                   │
│  ┌─────────┐  ┌─────────┐  ┌──────────────┐               │
│  │ReAct 图 │  │Tool 注册│  │ 提示词管理器 │               │
│  └─────────┘  └─────────┘  └──────────────┘               │
│       │              │              │                       │
│       ▼              ▼              ▼                       │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              ReAct 循环处理器 (核心)                  │   │
│  │  • 流式调用 LLM + Function Calling                   │   │
│  │  • 自动绑定工具集                                     │   │
│  │  • 工具执行（服务端沙盒 / 未来客户端）              │   │
│  │  • 观察结果注入消息列表                               │   │
│  │  • 回调推送思考、工具状态、最终答案                   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  工具集（已注册）                                           │
│  ├── query_knowledge      (混合检索 Milvus + Neo4j)        │
│  ├── extract_memories     (对话 → 知识库)                  │
│  ├── query_events_facts   (结构化事件/事实查询)            │
│  ├── web_search           (DuckDuckGo)                     │
│  ├── create_ppt / 其他云技能 (通过 SkillHub + Docker)      │
│  └── (预留) 客户端技能调用 (需要客户端守护程序)            │
└─────────────────────────────────────────────────────────────┘
```

- **客户端**：浏览器通过 WebSocket 与服务端智能体通信，显示思考过程、工具调用状态、最终答案，并可触发文件下载。
- **服务端**：LangGraph4j 编排的状态图，核心为 ReAct 循环节点，利用 LangChain4j 的 Function Calling 能力自动管理 Thought/Action/Observation 循环。
- **工具**：所有工具通过 `ToolRegistry` 统一管理，ReAct 循环在每次调用 LLM 时自动绑定当前可用工具集。

---

### 2. ReAct 循环实现细节

#### 2.1 核心循环逻辑（伪代码）

```java
public AgentState reactLoop(AgentState state, AgentStreamCallback callback) {
    List<ChatMessage> messages = buildInitialMessages(state); // 包含上下文和用户问题
    int step = 0;
    while (step < maxSteps) {
        // 1. 获取可用工具（可能会根据步骤或用户确认动态变化）
        List<ToolSpecification> tools = toolRegistry.getAvailableTools(state.getContext());

        // 2. 调用 LLM（支持流式），绑定工具
        var response = streamingChatModel.generate(messages, tools, new StreamingHandler(callback, step));

        // 3. 检查是否有工具调用
        if (response.hasToolExecutionRequests()) {
            callback.onToolCalls(response.toolExecutionRequests()); // 推送工具调用信息
            for (var toolRequest : response.toolExecutionRequests()) {
                // 4. 执行工具
                ToolExecutionResult result = toolRegistry.execute(toolRequest, state.getContext());
                // 5. 将工具结果包装成 FunctionMessage 加入消息列表
                messages.add(new ToolExecutionResultMessage(toolRequest.id(), toolRequest.name(), result.text()));
                callback.onToolResult(toolRequest.name(), result);
            }
        } else {
            // 6. 没有工具调用，即为最终答案，流式推送
            state.setFinalAnswer(response.content());
            break;
        }
        step++;
    }
    if (state.getFinalAnswer() == null) {
        // 超过最大步数，强制生成最终答案（可再次调用模型要求总结）
        state.setFinalAnswer(forceFinalAnswer(messages));
    }
    return state;
}
```

**关键点**：
- **完全依赖 Function Calling**，无需手动解析 Thought/Action/Observation 文本。模型在输出最终答案前会自然产生工具调用，内容文本本身可视为思考过程（我们通过系统提示词鼓励展示推理）。
- **流式处理**：`StreamingHandler` 实时解析模型返回的文本增量，在最终答案出来之前的内容都作为“思考”通过回调推送；如果模型返回工具调用，则暂时停止文本推送，转而推送工具调用事件。
- **循环控制**：最大步数可配置（默认 10），防止无限循环。
- **工具执行**：支持同步/异步，超时设置；异常时返回错误信息作为观察。

#### 2.2 流式回调设计

`AgentStreamCallback` 接口：

```java
public interface AgentStreamCallback {
    void onThink(String messageId, String token);          // 逐 token 推送思考内容
    void onToolCall(String stepId, ToolExecutionRequest req); // 即将调用某个工具
    void onToolResult(String stepId, String toolName, String summary, boolean success); // 工具执行结果
    void onFinalAnswer(String messageId, String token);    // 最终答案流式推送
    void onError(String message);                           // 异常信息
}
```

**前端效果**：
- 思考内容以灰色斜体字流式显示，工具调用显示为可折叠卡片（如“正在搜索：xxx”），执行完成后更新为“搜索完成，找到 3 条结果”。
- 最终答案以正常字体流式打印。

#### 2.3 提示词工程

**系统提示词模板** `react_system`：

```
你是智能助手 {agent_name}。你可以通过工具调用获取信息或执行操作。
{permanent_memories}

当前时间：{current_time}

**重要：在每次调用工具前，请先用简短的语言描述你的思考过程，然后直接返回工具调用。**
当你已经收集到足够信息时，请给出最终回答。

可用工具：
{tool_descriptions}
```

- `tool_descriptions` 由 `ToolRegistry` 自动生成，格式为 LangChain4j 兼容的 JSON。
- 该提示词确保模型在使用函数前输出思考文本，流式端可捕捉并作为 `Thought` 显示。

---

### 3. 工具集详细设计

#### 3.1 知识库查询工具 `query_knowledge`

- **功能**：从 Milvus + Neo4j 检索相关记忆。
- **参数**：`query_text`, `top_k`, `memory_types`
- **实现**：调用已有的 `KnowledgeQueryService.query()`。
- **返回**：格式化记忆列表（统一模板文本）。

#### 3.2 事件/事实提取工具 `extract_memories`

- **功能**：从对话文本中提取事件/事实并存入知识库。
- **参数**：`conversation_text`, `user_id`
- **实现**：调用提取模型按统一模板生成记忆条目，双写 Milvus + Neo4j。
- **返回**：提取数量及新建记忆ID列表。

#### 3.3 事件/事实查询工具 `query_events_facts`

- **功能**：通过结构化条件查询 Neo4j 中的事件/事实。
- **参数**：`type`, `keywords`, `time_start`, `time_end`, `tense`, `confidence`
- **实现**：动态构建 Cypher 查询，返回记忆列表。

#### 3.4 联网搜索工具 `web_search`

- **功能**：使用 DuckDuckGo API 搜索互联网。
- **参数**：`query`, `max_results`
- **实现**：调用封装好的 DDG 客户端，返回标题、URL、摘要列表。

#### 3.5 云端技能执行工具（预留接口）

- **技能商店技能**：如 `create_ppt`、`generate_image` 等，标记为 `execution_location=server`。
- **执行流程**：从 MinIO 下载脚本 → Docker 沙箱执行 → 生成文件并返回下载链接。
- **工具注册**：扫描 SkillHub 中激活的 server 技能，动态生成工具定义。
- **返回示例**：JSON 中包含文件下载链接。

#### 3.6 客户端技能调用（预留，v0.7 可设计接口但不接通）

- **设计**：在工具注册时检查 `execution_location`，若为 `client` 且当前会话绑定了客户端守护程序，则暴露工具。
- **执行**：通过 WebSocket 向客户端发送指令并等待结果。
- **v0.7 行为**：如果没有客户端连接，工具不可见，不会影响主流程。

---

### 4. 与客户端交互的全链路方案

即使在纯浏览器环境下，也能提供沉浸式体验：

1. **思考流**：WebSocket 持续推送 `onThink` token，前端实时展示。
2. **工具状态**：推送 `onToolCall` → 前端显示“正在调用搜索...”；`onToolResult` → 显示“搜索完成”并展示简要结果。
3. **最终答案**：`onFinalAnswer` 流式显示，并可附带富文本、下载链接等。
4. **文件生成**：若工具返回文件下载链接（如 PPT），前端自动弹出下载框或展示“下载”按钮。
5. **用户打断**：前端可发送中断指令，服务端停止循环并返回当前已获得的答案。

---

### 5. 错误处理与降级

- **工具超时**：设置每个工具执行超时（如 10 秒），超时则返回错误信息作为观察。
- **工具执行失败**：模型会收到失败消息，可尝试重新规划或告知用户。
- **最大步数达到**：强制使用“总结当前信息并回答问题”的指令再次调用模型。
- **模型不可用**：切换到备用对话模型（若配置了多个）。

---

### 6. 代码结构（v0.7 新增部分）

```
agent/
├── react/
│   ├── ReActGraphBuilder.java        // 构建 ReAct 图
│   ├── ReActLoopHandler.java         // 循环处理器
│   ├── ReActStreamingHandler.java    // 流式回调适配（与 LangChain4j 集成）
│   └── ReActPromptBuilder.java       // 提示词渲染
├── tool/
│   ├── impl/
│   │   ├── QueryKnowledgeTool.java
│   │   ├── ExtractMemoriesTool.java
│   │   ├── QueryEventsFactsTool.java
│   │   └── WebSearchTool.java
│   └── registry/
│       └── DynamicToolRegistry.java  // 增强：支持从 SkillHub 动态注册
└── callback/
    └── WebSocketStreamCallback.java  // WebSocket 桥接
```

---

### 7. 测试用例

1. **纯使用知识**：用户问“我的配偶叫什么？” → Agent 思考 → 调用 `query_knowledge` → 返回事实 → 回答。
2. **需要搜索**：用户问“今天天气如何？” → Agent 思考 → 调用 `web_search` → 得到结果 → 回答。
3. **记忆提取**：用户说“对了，我上周去了北京出差” → Agent 主动调用 `extract_memories` 存储。
4. **多步混合**：用户：“帮我查一下我上次去北京时住的酒店，再看看那附近有什么好吃的推荐” → Agent 先调用 `query_events_facts` 获取酒店名 → 再调用 `web_search` 搜索附近美食 → 综合回答。
5. **超限停止**：模拟工具无限循环，验证最大步数停止并给出友好答复。

---

### 8. 交付清单

- ✅ ReAct 图 & 循环处理器（含流式回调）
- ✅ 提示词模板 `react_system` 及渲染
- ✅ 四个核心工具（知识查询、记忆提取、事件查询、搜索）
- ✅ SkillHub 云端技能动态工具生成（可测试 create_ppt）
- ✅ WebSocket 回调适配器，支持思考/工具状态流式推送
- ✅ 错误处理与最大步数控制
- ✅ 集成 v0.6 的上下文管理、永驻记忆、会话持久化
- ✅ 测试套件

v0.7 上线后，你的智能体将能够自主进行多步推理、调用多种工具，并给用户带来实时的、透明的思考体验。这为后续 Plan&Execute、SubAgent 等高级模式奠定了坚实基础。

## 工具解析与调用详细实现

### 一、工具解析（LangChain4j 自动完成）

在你使用的 LangChain4j 框架中，**模型返回的流式响应本身已自动解析出工具调用**，无需手动编写文本解析器。关键类是 `AiMessage`，它包含两个核心部分：

- `text()`：模型输出的自然语言文本（在我们的提示词中，这部分是模型的“思考”过程）
- `toolExecutionRequests()`：如果有工具调用，返回 `List<ToolExecutionRequest>`

每个 `ToolExecutionRequest` 包含：
- `id()`：本次调用的唯一标识（用于关联返回结果）
- `name()`：工具名称
- `arguments()`：参数的 JSON 字符串

**代码片段（基于 StreamingResponseHandler）**

```java
streamingChatModel.generate(messages, toolSpecifications, new StreamingResponseHandler<AiMessage>() {
    @Override
    public void onNext(String token) {
        // 流式推送思考文本（工具调用前的自然语言）
        callback.onThink(currentMessageId, token);
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        AiMessage aiMessage = response.content();
        // 检查是否有工具调用
        if (aiMessage.hasToolExecutionRequests()) {
            // 输出思考部分（已通过 onNext 推送过）
            // 通知前端有工具调用
            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
            callback.onToolCalls(toolRequests);
            // 注意：实际的工具执行不在此 handler 内，需要返回到 ReAct 循环控制器中处理
            // 这里可将 aiMessage 保存到状态中，让主循环继续
        } else {
            // 最终答案，流式推送（实际上最终答案的 token 也通过 onNext 推送了）
            callback.onFinalAnswerComplete();
        }
    }

    @Override
    public void onError(Throwable error) {
        callback.onError(error.getMessage());
    }
});
```

在 ReAct 循环中，我们检查返回的 `AiMessage`，若存在工具请求，则依次执行工具，并将结果追加到消息列表。

---

### 二、工具调用分发

我们将所有工具（原生 Java、SkillHub 云端技能、未来客户端技能）统一抽象为 `ToolDefinition`，并在 `ToolRegistry` 中根据名称查找对应的执行逻辑。

**工具定义接口**

```java
public interface ToolDefinition {
    String getName();
    String getDescription();
    ToolParameters getParameters(); // JSON Schema
    /** 执行结果，context 可获取会话、用户等信息 */
    ToolExecutionResult execute(ToolExecutionRequest request, AgentContext context);
}
```

**注册中心核心方法**

```java
@Service
public class ToolRegistry {
    // key: toolName, value: ToolDefinition
    private final Map<String, ToolDefinition> toolMap = new ConcurrentHashMap<>();

    public void register(ToolDefinition tool) {
        toolMap.put(tool.getName(), tool);
    }

    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(toolMap.get(name));
    }

    public List<ToolSpecification> getAvailableSpecifications(AgentContext context) {
        // 根据权限、客户端能力过滤
        return toolMap.values().stream()
                .filter(tool -> isAllowed(tool, context))
                .map(tool -> ToolSpecification.builder()
                        .name(tool.getName())
                        .description(tool.getDescription())
                        .parameters(tool.getParameters().toJsonSchema())
                        .build())
                .collect(Collectors.toList());
    }
}
```

---

### 三、三种类型工具的实现

#### 1. 原生 Java 工具（在服务端直接执行）

例如我们之前写的 `query_knowledge`、`web_search` 等。

```java
@Component
public class QueryKnowledgeTool implements ToolDefinition {
    @Autowired private KnowledgeQueryService knowledgeQueryService;

    @Override
    public String getName() { return "query_knowledge"; }

    @Override
    public String getDescription() {
        return "从知识库中检索与查询文本相关的记忆条目（事件、事实、知识）。";
    }

    @Override
    public ToolParameters getParameters() {
        return ToolParameters.builder()
                .property("query_text", type("string"), "查询描述")
                .property("top_k", type("integer"), "返回条数，默认5")
                .optional("memory_types", type("array"), "限定记忆类型")
                .build();
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request, AgentContext context) {
        Map<String, Object> args = parseArgs(request.arguments());
        String queryText = (String) args.get("query_text");
        int topK = args.containsKey("top_k") ? (int) args.get("top_k") : 5;
        List<String> types = (List<String>) args.get("memory_types");
        // 调用服务
        List<MemoryItem> results = knowledgeQueryService.query(queryText, topK, types);
        return ToolExecutionResult.success(formatResults(results));
    }
}
```

**注册**：在应用启动时扫描所有实现了 `ToolDefinition` 的 Bean 并注册到 `ToolRegistry`。

---

#### 2. 云端技能（SkillHub 中 `execution_location = server` 的技能）

这类工具的任务在服务端 Docker 沙箱中执行（如生成 PPT、图片处理等）。我们需要一个**动态工具适配器**，它在加载时从 SkillHub 表中读取激活的技能，为每个技能生成一个 `ToolDefinition`。

**动态工具定义生成器**

```java
@Service
public class SkillHubToolRegistrar {
    @Autowired private SkillService skillService;
    @Autowired private ToolRegistry toolRegistry;

    @PostConstruct
    public void registerServerSkills() {
        List<Skill> serverSkills = skillService.getActiveSkillsByLocation("server");
        for (Skill skill : serverSkills) {
            ToolDefinition toolDef = new ServerSkillTool(skill);
            toolRegistry.register(toolDef);
        }
    }
}

public class ServerSkillTool implements ToolDefinition {
    private final Skill skill;
    private final SkillExecutionService executionService; // 注入或通过构造传入

    @Override
    public String getName() { return skill.getSkillCode(); }

    @Override
    public String getDescription() { return skill.getDescription(); }

    @Override
    public ToolParameters getParameters() {
        // 从 skill.metadataJson 中解析参数 schema 并构建
        return ToolParameters.fromJson(skill.getMetadataJson().get("parameters"));
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request, AgentContext context) {
        // 1. 从 MinIO 下载脚本到临时目录（如果尚未缓存）
        Path scriptDir = executionService.prepareScript(skill);
        // 2. 构建 Docker 执行命令（或直接调用本地解释器）
        String cmd = buildCommand(skill, request.arguments());
        // 3. 在 Docker 沙箱中执行，超时 60 秒
        ExecutionResult execResult = dockerSandbox.run(scriptDir, cmd, 60);
        // 4. 如果生成文件，上传至 MinIO 并生成下载链接
        String downloadUrl = null;
        if (execResult.exitCode() == 0 && Files.exists(scriptDir.resolve("output"))) {
            downloadUrl = fileService.uploadAndGenerateLink(scriptDir.resolve("output/file.pptx"));
        }
        // 5. 返回结果（包含输出文本和文件链接）
        return ToolExecutionResult.success(execResult.stdout(), downloadUrl);
    }
}
```

**Docker 沙箱执行示例**

```java
public ExecutionResult run(Path workDir, String command, int timeoutSec) {
    String containerName = "skill-exec-" + UUID.randomUUID().toString().substring(0,8);
    // 构建 Docker run 命令，挂载 workDir 到 /workspace，网络 none，只读根文件系统等
    String[] dockerCmd = {
        "docker", "run", "--rm",
        "--name", containerName,
        "--network", "none",
        "--memory", "512m",
        "--cpus", "1",
        "-v", workDir.toAbsolutePath() + ":/workspace",
        "skill-runner-python:3.9",  // 预制的镜像含 python3 等
        "bash", "-c", command
    };
    // 执行并等待，捕获输出...
}
```

---

#### 3. 客户端技能（需要客户端守护程序）

客户端技能在工具注册时，需要判断当前会话是否绑定了一个活跃的客户端守护程序。如果存在，才暴露该工具。

**动态过滤**

```java
public List<ToolSpecification> getAvailableSpecifications(AgentContext context) {
    return toolMap.values().stream()
            .filter(tool -> {
                if (tool instanceof ClientSkillTool) {
                    return context.getClientSession() != null; // 有活跃客户端连接
                }
                return true;
            })
            .map(...)
            .collect(...);
}
```

**客户端工具执行**

```java
public class ClientSkillTool implements ToolDefinition {
    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request, AgentContext context) {
        WebSocketSession clientSession = context.getClientSession();
        if (clientSession == null || !clientSession.isOpen()) {
            return ToolExecutionResult.failure("客户端未连接");
        }
        String taskId = UUID.randomUUID().toString();
        // 构建指令
        ClientCommand cmd = ClientCommand.builder()
                .taskId(taskId)
                .skillCode(getName())
                .version(skill.getVersion())
                .params(request.arguments())
                .requireConfirmation(skill.isRequireConfirmation())
                .build();
        // 发送给客户端，并注册一个 Future 等待结果
        CompletableFuture<ClientResult> future = new CompletableFuture<>();
        pendingRequests.put(taskId, future);
        clientSession.sendText(JsonUtils.toJson(cmd));
        try {
            ClientResult result = future.get(30, TimeUnit.SECONDS); // 超时 30s
            return ToolExecutionResult.success(result.output);
        } catch (TimeoutException e) {
            pendingRequests.remove(taskId);
            return ToolExecutionResult.failure("客户端执行超时");
        }
    }
}
```

**服务端 WebSocket 消息处理（接收结果）**

```java
@OnMessage
public void onMessage(WebSocketSession session, String message) {
    ClientResult result = JsonUtils.parse(message, ClientResult.class);
    CompletableFuture<ClientResult> future = pendingRequests.remove(result.getTaskId());
    if (future != null) {
        future.complete(result);
    }
}
```

---

### 四、工具执行结果封装与上下文注入

工具执行结束后，我们创建一个 `ToolExecutionResultMessage` 并追加到对话历史中，让 LLM 看到观察结果。

```java
// 在 ReAct 循环中
for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
    Optional<ToolDefinition> toolOpt = toolRegistry.getTool(req.name());
    if (toolOpt.isEmpty()) {
        messages.add(ToolExecutionResultMessage.from(req,
                ToolExecutionResult.failure("未知工具: " + req.name())));
        continue;
    }
    ToolExecutionResult result = toolOpt.get().execute(req, context);
    messages.add(ToolExecutionResultMessage.from(req, result));
    callback.onToolResult(req, result);
}
```

`ToolExecutionResultMessage` 是 LangChain4j 内置的消息类型，能够正确传递工具执行结果。

---

### 五、流式回调中的工具事件展示

我们扩展的 `AgentStreamCallback` 在工具调用和结果阶段被触发，前端据此展示动态卡片。

```java
public interface AgentStreamCallback {
    void onThink(String messageId, String token);
    void onToolCalls(List<ToolExecutionRequest> requests);
    void onToolResult(ToolExecutionRequest request, ToolExecutionResult result);
    void onFinalAnswer(String messageId, String token);
}
```

前端监听 WebSocket 消息，JSON 结构类似：

```json
{"type":"think","data":{"messageId":"msg1","token":"我需要"}}
{"type":"think","data":{"messageId":"msg1","token":"查询"}}
{"type":"tool_call","data":{"requests":[{"name":"web_search","args":"..."}]}}
{"type":"tool_result","data":{"requestId":"...","name":"web_search","success":true,"summary":"找到3条结果"}}
{"type":"final_answer","data":{"messageId":"msg2","token":"根据"}}
```

---

这套机制完整支撑了 ReAct 模式下的工具解析与分发，既利用了 LangChain4j 的原生能力，又为后续多环境执行提供了统一的抽象。v0.7 的实现即可在此基础上顺畅运行。