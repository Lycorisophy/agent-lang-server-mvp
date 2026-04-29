package cn.lysoy.agentlangservermvp.service;

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
import cn.lysoy.agentlangservermvp.util.ContentMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 对话写库事务边界：会话创建/加载与外表、内表插入在同一短事务内提交，供 {@link ChatService} 调用。
 */
@Service
public class ChatWriteFacade {

    private static final int TITLE_MAX_LEN = 120;

    private final ChatSessionMapper chatSessionMapper;
    private final OuterMessageMapper outerMessageMapper;
    private final InnerMessageMapper innerMessageMapper;

    public ChatWriteFacade(ChatSessionMapper chatSessionMapper,
                           OuterMessageMapper outerMessageMapper,
                           InnerMessageMapper innerMessageMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.outerMessageMapper = outerMessageMapper;
        this.innerMessageMapper = innerMessageMapper;
    }

    /**
     * 创建或加载会话并写入本轮用户的外表与内表消息。
     *
     * @return 会话 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String saveUserRound(String sessionId, String userId, String prompt) {
        ChatSession session = loadOrCreateSession(sessionId, userId, prompt);
        insertOuter(session.getId(), ChatConstants.ROLE_USER, prompt);
        insertInner(session.getId(), ChatConstants.ROLE_USER, prompt);
        return session.getId();
    }

    /**
     * 写入本轮助手的外表与内表消息。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAssistantRound(String sessionId, String reply) {
        insertOuter(sessionId, ChatConstants.ROLE_ASSISTANT, reply);
        insertInner(sessionId, ChatConstants.ROLE_ASSISTANT, reply);
    }

    private ChatSession loadOrCreateSession(String sessionId, String userId, String firstPrompt) {
        if (sessionId == null || sessionId.isBlank()) {
            return insertNewSession(userId, firstPrompt);
        }
        ChatSession existing = chatSessionMapper.selectById(sessionId.trim());
        if (existing == null || !Integer.valueOf(0).equals(existing.getDelFlag())) {
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
