package cn.lysoy.agentlangservermvp.service;

/**
 * 会话内异步压缩检查与执行（Redis/内存锁、管道摘要），失败不抛出到对话线程。
 */
public interface InnerMessageCompressService {

    /**
     * 在助手回合落库后调用；内含分布式/本地锁、阈值判断与各管道压缩。
     */
    void compressIfNeeded(String sessionId);
}
