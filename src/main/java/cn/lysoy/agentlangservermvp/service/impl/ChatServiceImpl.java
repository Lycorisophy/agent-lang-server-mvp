package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.dto.chat.ChatHttpResponse;
import cn.lysoy.agentlangservermvp.dto.chat.ChatStreamOutcome;
import cn.lysoy.agentlangservermvp.dto.chat.OuterMessageView;
import cn.lysoy.agentlangservermvp.integration.LangChainChatModelFactory;
import cn.lysoy.agentlangservermvp.mapper.ChatSessionMapper;
import cn.lysoy.agentlangservermvp.mapper.InnerMessageMapper;
import cn.lysoy.agentlangservermvp.mapper.OuterMessageMapper;
import cn.lysoy.agentlangservermvp.model.ChatSession;
import cn.lysoy.agentlangservermvp.model.InnerMessage;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.model.OuterMessage;
import cn.lysoy.agentlangservermvp.service.IChatModelResolutionService;
import cn.lysoy.agentlangservermvp.service.IChatService;
import cn.lysoy.agentlangservermvp.service.IChatWriteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link IChatService} 实现：委托 {@link IChatWriteService} 落库，从 {@code inner_message} 拼装 LangChain4j 上下文并调用大模型。
 */
@Service
public class ChatServiceImpl implements IChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final OuterMessageMapper outerMessageMapper;
    private final InnerMessageMapper innerMessageMapper;
    private final IChatModelResolutionService chatModelResolutionService;
    private final LangChainChatModelFactory langChainChatModelFactory;
    private final IChatWriteService chatWriteService;

    public ChatServiceImpl(ChatSessionMapper chatSessionMapper,
                           OuterMessageMapper outerMessageMapper,
                           InnerMessageMapper innerMessageMapper,
                           IChatModelResolutionService chatModelResolutionService,
                           LangChainChatModelFactory langChainChatModelFactory,
                           IChatWriteService chatWriteService) {
        this.chatSessionMapper = chatSessionMapper;
        this.outerMessageMapper = outerMessageMapper;
        this.innerMessageMapper = innerMessageMapper;
        this.chatModelResolutionService = chatModelResolutionService;
        this.langChainChatModelFactory = langChainChatModelFactory;
        this.chatWriteService = chatWriteService;
    }

    /**
     * HTTP 同步对话：用户消息落库后调用同步模型，再将完整助手回复落库并返回。
     * <p>
     * 【可异步化】若需进一步缩短 HTTP 阻塞时间，可在上游网关/队列先受理请求，再由消费者调用本逻辑；
     * 或在保证事务一致的前提下，将「助手落库」改为异步事件（需额外补偿与失败重试设计）。
     * </p>
     */
    @Override
    public ChatHttpResponse chatSync(String sessionId, Long modelId, String modelCode, String prompt, String userId) {
        ModelRegistry model = chatModelResolutionService.resolve(modelId, modelCode);
        String sid = chatWriteService.saveUserRound(sessionId, userId, prompt);
        List<ChatMessage> messages = loadContextMessages(sid);
        String reply;
        try {
            reply = langChainChatModelFactory.createSync(model).generate(messages).content().text();
        } catch (Exception ex) {
            throw new BusinessException(
                    ErrorCodeConstants.LLM_UPSTREAM_ERROR,
                    MessageConstants.format(MessageConstants.LLM_UPSTREAM_ERROR, ex.getMessage()),
                    ex
            );
        }
        chatWriteService.saveAssistantRound(sid, reply);
        return new ChatHttpResponse(sid, reply, model.getModelCode());
    }

    /**
     * WebSocket 流式对话：用户轮次落库后，以流式模型回调 {@code onDelta}，结束后将完整回复落库。
     * <p>
     * 【可异步化】当前在 WebSocket 线程上执行整段推理；若上游 SDK 支持纯异步 API，可将
     * {@code streaming.generate(...)} 提交到 {@code applicationTaskExecutor} 执行，但须保证
     * {@code onDelta} 与 WebSocket 发送仍在有序线程或加锁（参见 {@link cn.lysoy.agentlangservermvp.websocket.ChatWebSocketHandler}），
     * 并传递 MDC/租户上下文。
     * </p>
     */
    @Override
    public ChatStreamOutcome chatStream(String sessionId, Long modelId, String modelCode, String prompt, String userId,
                                        Consumer<String> onDelta) {
        ModelRegistry model = chatModelResolutionService.resolve(modelId, modelCode);
        String sid = chatWriteService.saveUserRound(sessionId, userId, prompt);
        List<ChatMessage> messages = loadContextMessages(sid);
        StreamingChatLanguageModel streaming = langChainChatModelFactory.createStreaming(model);
        StringBuilder acc = new StringBuilder();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        streaming.generate(messages, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                if (token != null && !token.isEmpty()) {
                    acc.append(token);
                    onDelta.accept(token);
                }
            }

            /**
             * 部分厂商实现仅在完成时给出完整文本；若此前无增量，则用此处文本兜底。
             */
            @Override
            public void onComplete(Response<AiMessage> response) {
                AiMessage ai = response != null ? response.content() : null;
                if (ai != null && ai.text() != null && !ai.text().isEmpty() && acc.isEmpty()) {
                    acc.append(ai.text());
                }
            }

            @Override
            public void onError(Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new BusinessException(
                    ErrorCodeConstants.LLM_UPSTREAM_ERROR,
                    MessageConstants.format(MessageConstants.LLM_UPSTREAM_ERROR, failure.get().getMessage()),
                    failure.get()
            );
        }
        String full = acc.toString();
        chatWriteService.saveAssistantRound(sid, full);
        return new ChatStreamOutcome(sid, full);
    }

    /**
     * 按会话查询外表历史，按创建时间升序，供前端渲染。
     * <p>
     * 【可异步化】当单会话消息量极大时，可改为分页查询或读从库，并在 Controller 层配合
     * {@link org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody} 流式输出（非必须）。
     * </p>
     */
    @Override
    public List<OuterMessageView> listOuterHistory(String sessionId) {
        requireExistingSession(sessionId);
        LambdaQueryWrapper<OuterMessage> q = new LambdaQueryWrapper<OuterMessage>()
                .eq(OuterMessage::getSessionId, sessionId)
                .orderByAsc(OuterMessage::getCreateAt);
        List<OuterMessage> rows = outerMessageMapper.selectList(q);
        List<OuterMessageView> views = new ArrayList<>(rows.size());
        for (OuterMessage row : rows) {
            views.add(new OuterMessageView(row.getId(), row.getSessionId(), row.getRole(), row.getContent(), row.getCreateAt()));
        }
        return views;
    }

    /**
     * 校验会话存在且未被逻辑删除（{@code selectById} 对已删行返回 {@code null}）。
     */
    private void requireExistingSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "sessionId 不能为空");
        }
        ChatSession existing = chatSessionMapper.selectById(sessionId.trim());
        if (existing == null) {
            throw new BusinessException(
                    ErrorCodeConstants.SESSION_NOT_FOUND,
                    MessageConstants.format(MessageConstants.SESSION_NOT_FOUND, sessionId)
            );
        }
    }

    /**
     * 读取内表消息并映射为 LangChain4j {@link ChatMessage} 列表，作为模型多轮上下文。
     * 未识别的 {@code role} 将跳过，避免非法消息类型导致上游报错。
     */
    private List<ChatMessage> loadContextMessages(String sessionId) {
        LambdaQueryWrapper<InnerMessage> q = new LambdaQueryWrapper<InnerMessage>()
                .eq(InnerMessage::getSessionId, sessionId)
                .orderByAsc(InnerMessage::getCreateAt);
        List<InnerMessage> rows = innerMessageMapper.selectList(q);
        List<ChatMessage> messages = new ArrayList<>(rows.size());
        for (InnerMessage row : rows) {
            String role = row.getRole();
            String text = row.getContent();
            if (ChatConstants.ROLE_USER.equalsIgnoreCase(role)) {
                messages.add(UserMessage.from(text));
            } else if (ChatConstants.ROLE_ASSISTANT.equalsIgnoreCase(role)) {
                messages.add(AiMessage.from(text));
            } else if (ChatConstants.ROLE_SYSTEM.equalsIgnoreCase(role)) {
                messages.add(SystemMessage.from(text));
            }
        }
        return messages;
    }
}
