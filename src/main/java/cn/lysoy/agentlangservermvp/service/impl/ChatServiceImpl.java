package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.config.AsyncExecutorConfiguration;
import cn.lysoy.agentlangservermvp.config.properties.AppContextCompressionProperties;
import cn.lysoy.agentlangservermvp.dto.chat.ChatHttpResponse;
import cn.lysoy.agentlangservermvp.dto.chat.ChatStreamOutcome;
import cn.lysoy.agentlangservermvp.dto.chat.OuterMessageView;
import cn.lysoy.agentlangservermvp.integration.LangChainChatModelFactory;
import cn.lysoy.agentlangservermvp.mapper.ChatSessionMapper;
import cn.lysoy.agentlangservermvp.mapper.OuterMessageMapper;
import cn.lysoy.agentlangservermvp.model.ChatSession;
import cn.lysoy.agentlangservermvp.model.InnerMessage;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.model.OuterMessage;
import cn.lysoy.agentlangservermvp.service.IChatModelResolutionService;
import cn.lysoy.agentlangservermvp.service.IChatService;
import cn.lysoy.agentlangservermvp.service.IChatWriteService;
import cn.lysoy.agentlangservermvp.service.InnerMessageCompressService;
import cn.lysoy.agentlangservermvp.service.InnerMessageContextService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

    private static final Logger log = LogManager.getLogger(ChatServiceImpl.class);
    private static final int DEFAULT_HISTORY_LIMIT = 10;
    private static final int MAX_HISTORY_LIMIT = 100;

    private final ChatSessionMapper chatSessionMapper;
    private final OuterMessageMapper outerMessageMapper;
    private final IChatModelResolutionService chatModelResolutionService;
    private final LangChainChatModelFactory langChainChatModelFactory;
    private final IChatWriteService chatWriteService;
    private final InnerMessageContextService innerMessageContextService;
    private final AppContextCompressionProperties appContextCompressionProperties;
    private final InnerMessageCompressService innerMessageCompressService;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public ChatServiceImpl(ChatSessionMapper chatSessionMapper,
                           OuterMessageMapper outerMessageMapper,
                           IChatModelResolutionService chatModelResolutionService,
                           LangChainChatModelFactory langChainChatModelFactory,
                           IChatWriteService chatWriteService,
                           InnerMessageContextService innerMessageContextService,
                           AppContextCompressionProperties appContextCompressionProperties,
                           InnerMessageCompressService innerMessageCompressService,
                           @Qualifier(AsyncExecutorConfiguration.APPLICATION_TASK_EXECUTOR)
                           ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.chatSessionMapper = chatSessionMapper;
        this.outerMessageMapper = outerMessageMapper;
        this.chatModelResolutionService = chatModelResolutionService;
        this.langChainChatModelFactory = langChainChatModelFactory;
        this.chatWriteService = chatWriteService;
        this.innerMessageContextService = innerMessageContextService;
        this.appContextCompressionProperties = appContextCompressionProperties;
        this.innerMessageCompressService = innerMessageCompressService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public ChatHttpResponse chatSync(String sessionId, Long modelId, String modelCode, String prompt, String userId) {
        int promptLen = prompt == null ? 0 : prompt.length();
        log.info(
                "chat_sync_start sessionId={} modelId={} modelCode={} userId={} promptChars={}",
                sessionId,
                modelId,
                modelCode,
                userId,
                promptLen
        );

        ModelRegistry model = chatModelResolutionService.resolve(modelId, modelCode);
        log.info(
                "chat_sync_resolved_model modelCode={} provider={} isMultimodal={}",
                model.getModelCode(),
                model.getProvider(),
                model.getIsMultimodal()
        );

        String sid = chatWriteService.saveUserRound(sessionId, userId, prompt);
        log.info("chat_sync_user_round_saved sessionId={}", sid);

        List<ChatMessage> messages = buildChatContextForModel(sid, model);
        log.info("chat_sync_context_ready sessionId={} chatMessageCount={}", sid, messages.size());

        String reply;
        long t0 = System.nanoTime();
        try {
            reply = langChainChatModelFactory.createSync(model).generate(messages).content().text();
        } catch (Exception ex) {
            log.error("chat_sync_llm_failed sessionId={} modelCode={}", sid, model.getModelCode(), ex);
            throw new BusinessException(
                    ErrorCodeConstants.LLM_UPSTREAM_ERROR,
                    MessageConstants.format(MessageConstants.LLM_UPSTREAM_ERROR, ex.getMessage()),
                    ex
            );
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        log.info(
                "chat_sync_llm_ok sessionId={} modelCode={} replyChars={} durationMs={}",
                sid,
                model.getModelCode(),
                reply == null ? 0 : reply.length(),
                ms
        );

        chatWriteService.saveAssistantRound(sid, reply);
        log.info("chat_sync_assistant_saved sessionId={}", sid);
        scheduleCompress(sid);
        return new ChatHttpResponse(sid, reply, model.getModelCode());
    }

    @Override
    public ChatStreamOutcome chatStream(String sessionId, Long modelId, String modelCode, String prompt, String userId,
                                        Consumer<String> onDelta) {
        int promptLen = prompt == null ? 0 : prompt.length();
        log.info(
                "chat_stream_start sessionId={} modelId={} modelCode={} userId={} promptChars={}",
                sessionId,
                modelId,
                modelCode,
                userId,
                promptLen
        );

        ModelRegistry model = chatModelResolutionService.resolve(modelId, modelCode);
        log.info(
                "chat_stream_resolved_model modelCode={} provider={} isMultimodal={}",
                model.getModelCode(),
                model.getProvider(),
                model.getIsMultimodal()
        );

        String sid = chatWriteService.saveUserRound(sessionId, userId, prompt);
        log.info("chat_stream_user_round_saved sessionId={}", sid);

        List<ChatMessage> messages = buildChatContextForModel(sid, model);
        log.info("chat_stream_context_ready sessionId={} chatMessageCount={}", sid, messages.size());

        StreamingChatLanguageModel streaming = langChainChatModelFactory.createStreaming(model);
        StringBuilder acc = new StringBuilder();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        long t0 = System.nanoTime();
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
                log.debug("chat_stream_onComplete sessionId={} accChars={}", sid, acc.length());
            }

            @Override
            public void onError(Throwable error) {
                log.error("chat_stream_onError sessionId={}", sid, error);
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
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        String full = acc.toString();
        log.info(
                "chat_stream_llm_done sessionId={} modelCode={} fullReplyChars={} durationMs={}",
                sid,
                model.getModelCode(),
                full.length(),
                ms
        );

        chatWriteService.saveAssistantRound(sid, full);
        log.info("chat_stream_assistant_saved sessionId={}", sid);
        scheduleCompress(sid);
        return new ChatStreamOutcome(sid, full);
    }

    @Override
    public List<OuterMessageView> listOuterHistory(String sessionId, Long beforeId, Integer limit) {
        int pageSize = normalizeHistoryLimit(limit);
        log.debug("list_outer_history sessionId={} beforeId={} limit={}", sessionId, beforeId, pageSize);
        requireExistingSession(sessionId);
        LambdaQueryWrapper<OuterMessage> q = new LambdaQueryWrapper<OuterMessage>()
                .eq(OuterMessage::getSessionId, sessionId);
        if (beforeId != null && beforeId > 0) {
            q.lt(OuterMessage::getId, beforeId);
        }
        q.orderByDesc(OuterMessage::getId)
                .last("LIMIT " + pageSize);
        List<OuterMessage> rows = outerMessageMapper.selectList(q);
        log.info("list_outer_history_done sessionId={} beforeId={} rows={}", sessionId, beforeId, rows.size());
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
        if (existing == null) {
            log.warn("session_not_found sessionId={}", sessionId);
            throw new BusinessException(
                    ErrorCodeConstants.SESSION_NOT_FOUND,
                    MessageConstants.format(MessageConstants.SESSION_NOT_FOUND, sessionId)
            );
        }
    }

    private List<ChatMessage> buildChatContextForModel(String sessionId, ModelRegistry model) {
        if (Boolean.TRUE.equals(model.getIsMultimodal())) {
            log.info("multimodal_stub sessionId={} modelCode={} — 附件/多模态正文尚未接入", sessionId, model.getModelCode());
        }
        int maxTokens = appContextCompressionProperties.getContext().getMaxTokens();
        var truncate = innerMessageContextService.buildContextWithTruncation(sessionId, maxTokens);
        if (truncate.droppedMessageCount() > 0) {
            log.info(
                    "context_truncated_summary sessionId={} droppedMsgs={} droppedTokensApprox={} totalTokensBefore={} maxTokens={}",
                    sessionId,
                    truncate.droppedMessageCount(),
                    truncate.droppedTokenApprox(),
                    truncate.totalTokensBefore(),
                    maxTokens
            );
        }
        List<ChatMessage> mapped = mapInnerRowsToMessages(truncate.messages());
        log.debug(
                "build_chat_context sessionId={} llmRolesMapped={}",
                sessionId,
                mapped.size()
        );
        return mapped;
    }

    private static List<ChatMessage> mapInnerRowsToMessages(List<InnerMessage> rows) {
        List<ChatMessage> messages = new ArrayList<>(rows.size());
        for (InnerMessage row : rows) {
            String role = row.getRole();
            String text = row.getContent();
            if (ChatConstants.ROLE_USER.equalsIgnoreCase(role)) {
                messages.add(UserMessage.from(text));
            } else if (isAssistantRole(role)) {
                messages.add(AiMessage.from(text));
            } else if (ChatConstants.ROLE_SYSTEM.equalsIgnoreCase(role)) {
                messages.add(SystemMessage.from(text));
            } else {
                log.warn("skip_inner_row_unknown_role innerRole={}", role);
            }
        }
        return messages;
    }

    private static boolean isAssistantRole(String role) {
        if (role == null) {
            return false;
        }
        return ChatConstants.ROLE_ASSISTANT.equalsIgnoreCase(role)
                || ChatConstants.ROLE_ASSISTANT_REPLY.equalsIgnoreCase(role)
                || ChatConstants.ROLE_ASSISTANT_THOUGHT.equalsIgnoreCase(role);
    }

    private void scheduleCompress(String sessionId) {
        applicationTaskExecutor.execute(() -> {
            log.debug("compress_scheduled_submit sessionId={}", sessionId);
            try {
                innerMessageCompressService.compressIfNeeded(sessionId);
            } catch (Exception ex) {
                log.warn("compress_async_failed sessionId={}", sessionId, ex);
            }
        });
    }

    private static int normalizeHistoryLimit(Integer raw) {
        if (raw == null || raw <= 0) {
            return DEFAULT_HISTORY_LIMIT;
        }
        return Math.min(raw, MAX_HISTORY_LIMIT);
    }
}
