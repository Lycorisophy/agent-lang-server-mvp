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

    /**
     * 外表历史瀑布流查询：按 id 倒序返回，若传入 beforeId 则仅返回更早消息（id < beforeId）。
     *
     * @param sessionId 会话 ID
     * @param beforeId  向上翻页时携带本地最早一条 id；首次加载可为空
     * @param limit     返回条数上限，默认 10
     */
    List<OuterMessageView> listOuterHistory(String sessionId, Long beforeId, Integer limit);
}
