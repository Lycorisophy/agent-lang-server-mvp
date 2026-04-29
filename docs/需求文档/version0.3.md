以下是完整的上下文管理设计方案，涵盖提交大模型前后的所有环节，包括消息存储、上下文组装、异步压缩、同步截断等。

---

## 一、整体架构

上下文管理分为三大部分：
1. **消息产生与存储**：用户输入和模型回复分别写入内表、外表。
2. **上下文组装**：每次调用大模型前，从内表组装消息并兜底截断。
3. **异步压缩**：模型回复完成后异步检查总 token，触发多管道压缩。

控制参数（可配置）：
- `context.max_tokens`：上下文窗口上限（如 8000）
- `compress.trigger_threshold`：触发异步压缩的 token 数（如 7000，低于截断上限提前瘦身）
- `compress.min_saved_tokens`：压缩后至少节省的 token 比例（如 30%），否则不执行压缩

---

## 二、数据库实体设计（内表 `inner_message`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 主键 |
| session_id | varchar(36) | 会话ID |
| role | varchar(20) | `user` / `assistant/thought` / `assistant/reply` / `system`（摘要） |
| content | text | 消息内容 |
| content_length | int | 当前内容长度 |
| token_count | int | token 估算值 |
| compressed_length | int | 压缩前原始长度（仅压缩消息记录） |
| compress_method | varchar(20) | `summary` 摘要、`truncation` 截断、`none` 未压缩 |
| del_flag | tinyint(1) | 逻辑删除标记 |
| create_by / update_by | varchar(64) | 操作者 |
| create_at / update_at | datetime(3) | 时间戳（毫秒精度） |

设计要点：
- 深度思考内容单独存储为 `assistant/thought`，最终回复为 `assistant/reply`，便于差异化压缩。
- 未压缩消息的 `compress_method = 'none'`，压缩后改为对应方式。
- `compressed_length` 记录压缩前总长度，便于分析压缩效果。

---

## 三、消息生命周期与上下文处理流程

### 3.1 用户发送消息时（提交模型前）

```
用户输入
  ↓
1. 保存 user 消息到 inner_message
   - role = 'user'
   - content = 原始输入
   - token_count = 估算值
   - compress_method = 'none'
  ↓
2. 组装上下文 → 调用 buildContextWithTruncation(sessionId, max_tokens)
   - 查询 session 下所有 del_flag=0 的消息，按 create_at 升序
   - 计算总 token，若 > max_tokens，从最早非 system 消息开始丢弃，直至满足阈值
   - 保留 system 角色消息（如摘要）不被丢弃
  ↓
3. 将截断后的消息列表发送给大模型
```

**buildContextWithTruncation 伪代码**：
```
totalTokens = sum(msg.token_count)
idx = 0
while totalTokens > max_tokens and idx < messages.size():
    if messages[idx].role != 'system':
        totalTokens -= messages[idx].token_count
        idx++
    else:
        idx++
return messages.subList(idx, messages.size())
```

### 3.2 大模型回复后

```
大模型返回结果（含 reasoning_content 和 content）
  ↓
1. 存储 assistant 消息到 inner_message
   - 若存在 reasoning_content，存为一条 role='assistant/thought'，content=reasoning_content
   - 存一条 role='assistant/reply'，content=content
   - 分别估算 token_count，compress_method='none'
  ↓
2. 同步保存外表消息（仅保存 reply 内容，用户可见）
  ↓
3. 异步触发压缩检查
   - 计算 session 下所有 inner_message 的总 token
   - 若 totalTokens > compress.trigger_threshold，启动压缩任务（加锁防并发）
```

---

## 四、异步压缩任务设计

### 4.1 压缩触发条件与加锁
- 使用 Redis 分布式锁，key = `compress:lock:{sessionId}`，过期时间 30 秒。
- 获取锁后重新计算最新总 token，若仍超阈值则执行压缩。

### 4.2 压缩范围
- 所有 `compress_method = 'none'` 且 `del_flag = 0` 的历史消息（包括 user 和 assistant），按时间升序处理，**保留最近 N 轮完整对话不压缩**（N 可配置，如 4 轮）。
- 最近 N 轮也超限时才对其进行压缩，但优先压缩早期部分。

### 4.3 三阶段管道压缩

压缩管道按顺序执行，上一阶段完成后若总 token 已达标则跳过后续阶段。

**管道一：删除过长的工具回复（若存在）**
- 识别角色为 `tool` 或含工具调用结果的消息。
- 若某条工具回复的 `content_length` 超过阈值（如 500 字符），直接逻辑删除该消息（设置 `del_flag=1`）。
- 一期无工具调用，此管道可空置，仅保留接口。

**管道二：清理无用消息（确定性规则）**
- 规则示例（可配置）：
    - 连续重复消息（内容完全相同）只保留一条。
    - 纯感谢、确认（如“谢谢”、“好的”、“明白了”）且长度极短（<10字符）且后一条消息是用户发出的，可删除该 assistant/reply 消息。
- 实现：遍历待压缩列表，按规则标记 `del_flag=1`。
- 注意：一期可暂不启用，避免误伤。

**管道三：中模型压缩深度思考与回复**
- 将未被删除的待压缩消息分组，每组若干条（如 6 条）交给“中模型”（比主模型便宜）生成摘要。
- **压缩强度差异**：
    - `assistant/thought`：深层压缩，摘要长度限制为原始总长的 20% 或 100 字符，重点保留决策逻辑。
    - `assistant/reply` 和 `user`：标准压缩，保留关键信息、实体、动作，摘要长度限制为原始总长的 40% 或 300 字符。
- 压缩后：
    - 将原始消息逻辑删除。
    - 插入一条 `role='system'` 的摘要消息，`content` 为摘要文本，`compress_method='summary'`，`compressed_length` 记录压缩前该组消息总长度，`token_count` 为新摘要的 token 数。
- 若分组处理后仍超阈值，对剩余未压缩部分继续分组压缩，直至总数达标或全部压缩。
- 压缩完成后评估节省比例，若节省 token 比例小于 `min_saved_tokens`（如 30%），可回滚本次压缩（保留原始消息并记录日志）。

---

## 五、同步截断作为最终保障

- 每次调用大模型前必须执行 `buildContextWithTruncation`，确保上下文不超限。
- 如果异步压缩尚未完成（比如锁未释放或任务排队），截断逻辑会自动丢弃最早消息，保证调用安全。
- 截断只影响本次发送的上下文，不修改数据库，下次请求仍会重新组装原始消息（若压缩已完成则发摘要，否则再次截断）。

---

## 六、关键边界处理

| 场景 | 处理方式 |
|------|----------|
| 同一会话并发请求 | 上下文组装为纯查询，无锁；异步压缩按 session 加锁，同一时间只有一个任务在执行。 |
| 压缩未完成时又有新消息 | 新消息立即存入 inner_message，`compress_method='none'`。即使压缩任务正在运行，它的工作集是启动时快照，新消息会参与下次压缩或截断。 |
| 压缩后总 token 仍超标 | 由同步截断兜底，优先丢弃旧消息。 |
| 摘要系统消息被截断 | 截断时优先保留 `system` 角色，确保摘要不丢失。 |
| 模型切换导致 token 估算偏差 | `token_count` 使用通用估算（如字符数/3），或接入具体模型的 tokenizer。允许误差，截断阈值可设置得略保守（如窗口的 90%）。 |
| 压缩失败或超时 | 记录错误日志，不阻塞对话，依赖同步截断保障调用安全。 |

---

## 七、日志与监控

记录以下指标用于调优：
- 每次截断丢弃的消息数、丢弃 token 数。
- 每次压缩的管道执行情况、压缩前后 token 数、节省比例。
- 压缩任务耗时、是否失败。
- 上下文超限次数。

---

## 八、接口示意

```java
// ChatService
public void onUserMessage(String sessionId, String userPrompt, String modelCode) {
    // 1. 保存 user 内表消息
    // 2. 组装上下文 + 截断
    List<InnerMessage> context = innerMessageService.buildContextWithTruncation(sessionId, maxTokens);
    // 3. 调用大模型
    // 4. 保存 assistant 消息（thought/reply）
    // 5. 异步触发压缩
    compressionService.compressIfNeeded(sessionId);
}
```

这样设计后，您的智能体服务端在长对话、高频交互下既能保证上下文质量，又能严格控制 token 成本，且具备完整的可观测性和容错能力。如果需要对某一部分（比如压缩管道具体实现、分布式锁集成）进一步细化，我可以继续提供示例代码。


根据您的需求——区分**对话、压缩、智能体**三种用途的模型，并标记是否支持**多模态**，同时考虑现有定时加载全量模型到内存的架构，我建议**直接在 `model_registry` 表增加标志字段**，而非新增关联表。这样内存缓存可直接利用，性能最优，也避免引入额外查询。

---

## 一、表结构变更

在 `model_registry` 表中增加以下字段（全部为 `tinyint(1)`，默认 0）：

```sql
ALTER TABLE `model_registry`
    ADD COLUMN `is_chat`       tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可用于对话',
    ADD COLUMN `is_compression` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可用于压缩（摘要等）',
    ADD COLUMN `is_agent`      tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可用于智能体（工具调用等）',
    ADD COLUMN `is_multimodal` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否具备多模态能力（图片、音频等）';
```

若已有数据，需根据实际情况手动更新这些字段，例如将当前模型标记为对话 + 压缩等。

---

## 二、实体类增强

`ModelRegistry` 实体增加对应属性：

```java
@Data
@TableName("model_registry")
public class ModelRegistry {
    // ...原有字段

    /** 是否可用于对话 */
    private Boolean isChat;

    /** 是否可用于压缩场景（如摘要） */
    private Boolean isCompression;

    /** 是否可用于智能体（Agent） */
    private Boolean isAgent;

    /** 是否支持多模态输入 */
    private Boolean isMultimodal;
}
```

---

## 三、调整模型选择逻辑

### 1. 对话模型选择（`ChatModelResolutionService`）

当前 `pickDefaultActiveModel()` 只取最新活跃模型，现在需要增加 `is_chat = true` 过滤条件，确保默认对话模型具备对话能力：

```java
private ModelRegistry pickDefaultActiveModel() {
    return configLoaderService.getAllModels().stream()
            .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
            .filter(m -> Boolean.TRUE.equals(m.getIsChat()))  // 新增过滤
            .max(Comparator.comparing(ModelRegistry::getCreateAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .orElseThrow(() -> new BusinessException(
                    ErrorCodeConstants.NO_ACTIVE_MODEL,
                    "没有可用的对话模型"
            ));
}
```

同样，在 `resolve(modelId, modelCode)` 中，当用户传入具体的 `modelId` 或 `modelCode` 时，也应校验该模型的 `isChat` 是否为真，否则抛出异常：“该模型未启用对话能力”。

---

### 2. 压缩模型选择（新增）

压缩场景需要一个独立的模型选择服务，过滤 `is_compression = true` 且活跃的模型。若未指定模型编码，默认选取最新创建的活跃压缩模型：

```java
@Service
public class CompressionModelResolutionService {

    private final IConfigLoaderService configLoaderService;

    public CompressionModelResolutionService(IConfigLoaderService configLoaderService) {
        this.configLoaderService = configLoaderService;
    }

    public ModelRegistry resolve(String modelCode) {
        if (modelCode != null && !modelCode.isBlank()) {
            ModelRegistry model = configLoaderService.getModelConfig(modelCode.trim());
            if (model == null || !Boolean.TRUE.equals(model.getIsActive())) {
                throw new BusinessException("压缩模型不可用");
            }
            if (!Boolean.TRUE.equals(model.getIsCompression())) {
                throw new BusinessException("该模型未启用压缩能力");
            }
            return model;
        }
        // 默认选取最新活跃的压缩模型
        return configLoaderService.getAllModels().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .filter(m -> Boolean.TRUE.equals(m.getIsCompression()))
                .max(Comparator.comparing(ModelRegistry::getCreateAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BusinessException("没有可用的压缩模型"));
    }
}
```

压缩服务（如 `InnerMessageCompressService`）调用此服务来获取用来生成摘要的“中模型”。

---

### 3. 智能体模型选择（预留）

类似地，未来智能体需要支持工具调用（Function Calling），选择时必须满足 `is_agent = true`。可创建 `AgentModelResolutionService` 遵循相同模式。

---

## 四、多模态能力的使用

当模型标记为 `is_multimodal = true` 时，在对话流程中允许传递图片、音频等资源。这主要在上下文构建时决定是否对用户消息中的附件做特殊处理（如调用视觉模型）。可在 `ChatService` 中获取当前模型后判断：

```java
ModelRegistry model = chatModelResolutionService.resolve(modelId, modelCode);
if (Boolean.TRUE.equals(model.getIsMultimodal())) {
    // 构建多模态消息内容（如包含图片 URL）
}
```

---

## 五、内存缓存影响

`ConfigLoaderServiceImpl` 定时全量加载 `model_registry` 到内存，现在实体已包含新字段，缓存自动拥有这些标志位。所有 `getModelConfig`、`getAllModels` 返回的数据均可直接判断能力，**无需额外数据库查询**，性能不受影响。

---

## 六、对现有功能的影响

1. **原有对话接口**：无代码变更，`ChatModelResolutionService` 增加能力校验后，`resolve` 方法行为兼容（若用户传入了一个未标记 `is_chat` 的旧模型，会收到错误提示，这是预期效果）。
2. **压缩功能**：初期可暂时不实现压缩，但表结构和选择服务就绪后，随时可以集成。
3. **管理后台**：需提供界面修改模型的能力标签（勾选 is_chat、is_compression 等），对应的 Controller 更新实体即可。

---

## 七、扩展性考虑

如果未来需要更细粒度的区分（例如支持的具体多模态类型 vision/audio），可以将 `is_multimodal` 替换为 `multimodal_types` 字段（JSON 或逗号分隔字符串），当下一个布尔值已足够推进一期。

---

**结论**：在 `model_registry` 表直接增加标志字段是最符合当前架构的方案，简单高效。配合过滤式的模型选择服务，即可清晰地区分各用途模型，并为后续异步压缩、智能体功能打下基础。