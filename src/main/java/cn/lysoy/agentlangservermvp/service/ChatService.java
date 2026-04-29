package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.dto.chat.ChatHttpResponse;
import cn.lysoy.agentlangservermvp.dto.chat.ChatStreamOutcome;
import cn.lysoy.agentlangservermvp.dto.chat.OuterMessageView;
import cn.lysoy.agentlangservermvp.mapper.ChatSessionMapper;
import cn.lysoy.agentlangservermvp.mapper.InnerMessageMapper;
import cn.lysoy.agentlangservermvp.mapper.OuterMessageMapper;
import cn.lysoy.agentlangservermvp.model.ChatSession;
import cn.lysoy.agentlangservermvp.model.InnerMessage;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.model.OuterMessage;
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

/**
 * 对话编排：委托 {@link ChatWriteFacade} 做短事务落库；从 {@code inner_message} 拼装 LangChain4j 上下文并调用大模型。
 */
@Service
public class ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final OuterMessageMapper outerMessageMapper;
    private final InnerMessageMapper innerMessageMapper;
    private final ChatModelResolutionService chatModelResolutionService;
    private final LangChainChatModelFactory langChainChatModelFactory;
    private final ChatWriteFacade chatWriteFacade;

    public ChatService(ChatSessionMapper chatSessionMapper,
                       OuterMessageMapper outerMessageMapper,
                       InnerMessageMapper innerMessageMapper,
                       ChatModelResolutionService chatModelResolutionService,
                       LangChainChatModelFactory langChainChatModelFactory,
                       ChatWriteFacade chatWriteFacade) {
        this.chatSessionMapper = chatSessionMapper;
        this.outerMessageMapper = outerMessageMapper;
        this.innerMessageMapper = innerMessageMapper;
        this.chatModelResolutionService = chatModelResolutionService;
        this.langChainChatModelFactory = langChainChatModelFactory;
        this.chatWriteFacade = chatWriteFacade;
    }

    /**
     * HTTP 同步对话：整段助手回复一次性返回。
     *
     * @param sessionId 可选续聊会话 ID
     * @param modelId   可选模型主键
     * @param modelCode 可选模型代码
     * @param prompt    用户输入
     * @param userId    可选用户标识
     * @return 会话 ID、完整回复、实际使用的模型代码
     */
    public ChatHttpResponse chatSync(String sessionId, Long modelId, String modelCode, String prompt, String userId) {
        ModelRegistry model = chatModelResolutionService.resolve(modelId, modelCode);
        String sid = chatWriteFacade.saveUserRound(sessionId, userId, prompt);
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
        chatWriteFacade.saveAssistantRound(sid, reply);
        return new ChatHttpResponse(sid, reply, model.getModelCode());
    }

    /**
     * WebSocket 流式对话：通过回调推送增量 token，结束时持久化助手消息并返回会话与全文。
     *
     * @param onDelta 收到模型增量文本时回调（单 WebSocket 会话内顺序调用）
     * @return 服务端确定的会话 ID 与完整助手文本
     */
    public ChatStreamOutcome chatStream(String sessionId, Long modelId, String modelCode, String prompt, String userId,
                                        java.util.function.Consumer<String> onDelta) {
        ModelRegistry model = chatModelResolutionService.resolve(modelId, modelCode);
        String sid = chatWriteFacade.saveUserRound(sessionId, userId, prompt);
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
        chatWriteFacade.saveAssistantRound(sid, full);
        return new ChatStreamOutcome(sid, full);
    }

    /**
     * 按会话查询外表历史消息，供前端渲染聊天记录。
     */
    public List<OuterMessageView> listOuterHistory(String sessionId) {
        requireExistingSession(sessionId);
        LambdaQueryWrapper<OuterMessage> q = new LambdaQueryWrapper<OuterMessage>()
                .eq(OuterMessage::getSessionId, sessionId)
                .eq(OuterMessage::getDelFlag, 0)
                .orderByAsc(OuterMessage::getCreateAt);
        List<OuterMessage> rows = outerMessageMapper.selectList(q);
        List<OuterMessageView> views = new ArrayList<>(rows.size());
        for (OuterMessage row : rows) {
            views.add(new OuterMessageView(row.getId(), row.getSessionId(), row.getRole(), row.getContent(), row.getCreateAt()));
        }
        return views;
    }

    private void requireExistingSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.VALIDATION_ERROR, "sessionId 不能为空");
        }
        ChatSession existing = chatSessionMapper.selectById(sessionId.trim());
        if (existing == null || !Integer.valueOf(0).equals(existing.getDelFlag())) {
            throw new BusinessException(
                    ErrorCodeConstants.SESSION_NOT_FOUND,
                    MessageConstants.format(MessageConstants.SESSION_NOT_FOUND, sessionId)
            );
        }
    }

    /**
     * 从 {@code inner_message} 读取未删除记录并转为 LangChain4j 消息列表。
     */
    private List<ChatMessage> loadContextMessages(String sessionId) {
        LambdaQueryWrapper<InnerMessage> q = new LambdaQueryWrapper<InnerMessage>()
                .eq(InnerMessage::getSessionId, sessionId)
                .eq(InnerMessage::getDelFlag, 0)
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
