package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.dto.chat.ChatHttpResponse;
import cn.lysoy.agentlangservermvp.dto.chat.ChatStreamOutcome;
import cn.lysoy.agentlangservermvp.dto.chat.OuterMessageView;

import java.util.List;
import java.util.function.Consumer;

/**
 * 对话编排：HTTP 同步、WebSocket 流式、外表历史查询。
 */
public interface IChatService {

    ChatHttpResponse chatSync(String sessionId, Long modelId, String modelCode, String prompt, String userId);

    ChatStreamOutcome chatStream(String sessionId, Long modelId, String modelCode, String prompt, String userId,
                                 Consumer<String> onDelta);

    List<OuterMessageView> listOuterHistory(String sessionId);
}
