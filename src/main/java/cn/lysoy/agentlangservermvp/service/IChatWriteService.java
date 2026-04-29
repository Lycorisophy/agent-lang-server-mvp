package cn.lysoy.agentlangservermvp.service;

/**
 * 对话写库事务边界：会话与用户/助手消息落库。
 */
public interface IChatWriteService {

    /**
     * 创建或加载会话并写入本轮用户的外表与内表消息。
     *
     * @return 会话 ID
     */
    String saveUserRound(String sessionId, String userId, String prompt);

    /**
     * 写入本轮助手的外表与内表消息。
     */
    void saveAssistantRound(String sessionId, String reply);
}
