package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.mapper.ChatSessionMapper;
import cn.lysoy.agentlangservermvp.mapper.InnerMessageMapper;
import cn.lysoy.agentlangservermvp.mapper.OuterMessageMapper;
import cn.lysoy.agentlangservermvp.model.ChatSession;
import cn.lysoy.agentlangservermvp.model.InnerMessage;
import cn.lysoy.agentlangservermvp.model.OuterMessage;
import cn.lysoy.agentlangservermvp.service.IChatWriteService;
import cn.lysoy.agentlangservermvp.util.ContentMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@link IChatWriteService} 实现：短事务写入会话与双表消息。
 */
@Service
public class ChatWriteServiceImpl implements IChatWriteService {

    private static final int TITLE_MAX_LEN = 120;

    private final ChatSessionMapper chatSessionMapper;
    private final OuterMessageMapper outerMessageMapper;
    private final InnerMessageMapper innerMessageMapper;

    public ChatWriteServiceImpl(ChatSessionMapper chatSessionMapper,
                                OuterMessageMapper outerMessageMapper,
                                InnerMessageMapper innerMessageMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.outerMessageMapper = outerMessageMapper;
        this.innerMessageMapper = innerMessageMapper;
    }

    /**
     * 创建或续用会话，并写入本轮用户消息到外表与内表（同一事务）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveUserRound(String sessionId, String userId, String prompt) {
        ChatSession session = loadOrCreateSession(sessionId, userId, prompt);
        insertOuter(session.getId(), ChatConstants.ROLE_USER, prompt);
        insertInner(session.getId(), ChatConstants.ROLE_USER, prompt);
        return session.getId();
    }

    /**
     * 将本轮助手完整回复写入外表与内表（同一事务）。
     * <p>
     * 【可异步化】若后续引入「流式落库分段写入」，可将多次 insert/update 拆到队列消费者中异步执行，
     * 需与 {@link cn.lysoy.agentlangservermvp.service.impl.ChatServiceImpl#chatStream} 的完成顺序协调。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAssistantRound(String sessionId, String reply) {
        insertOuter(sessionId, ChatConstants.ROLE_ASSISTANT, reply);
        insertInner(sessionId, ChatConstants.ROLE_ASSISTANT, reply);
    }

    /**
     * 逻辑删除会话在 {@link com.baomidou.mybatisplus.annotation.TableLogic} 下 {@link #selectById} 不会返回，故仅判断 null。
     */
    private ChatSession loadOrCreateSession(String sessionId, String userId, String firstPrompt) {
        if (sessionId == null || sessionId.isBlank()) {
            return insertNewSession(userId, firstPrompt);
        }
        ChatSession existing = chatSessionMapper.selectById(sessionId.trim());
        if (existing == null) {
            throw new BusinessException(
                    ErrorCodeConstants.SESSION_NOT_FOUND,
                    MessageConstants.format(MessageConstants.SESSION_NOT_FOUND, sessionId)
            );
        }
        return existing;
    }

    private ChatSession insertNewSession(String userId, String firstPrompt) {
        ChatSession s = new ChatSession();
        s.setId(UUID.randomUUID().toString());
        s.setUserId(userId);
        s.setTitle(buildTitle(firstPrompt));
        s.setDelFlag(0);
        s.setCreateAt(LocalDateTime.now());
        chatSessionMapper.insert(s);
        return s;
    }

    private static String buildTitle(String prompt) {
        String oneLine = prompt.replace('\n', ' ').trim();
        if (oneLine.length() <= TITLE_MAX_LEN) {
            return oneLine;
        }
        return oneLine.substring(0, TITLE_MAX_LEN);
    }

    private void insertOuter(String sessionId, String role, String content) {
        OuterMessage m = new OuterMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setContentLength(ContentMetrics.charLength(content));
        m.setDelFlag(0);
        m.setCreateAt(LocalDateTime.now());
        outerMessageMapper.insert(m);
    }

    private void insertInner(String sessionId, String role, String content) {
        InnerMessage m = new InnerMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setContentLength(ContentMetrics.charLength(content));
        m.setTokenCount(ContentMetrics.roughTokenEstimate(content));
        m.setDelFlag(0);
        m.setCreateAt(LocalDateTime.now());
        innerMessageMapper.insert(m);
    }
}
