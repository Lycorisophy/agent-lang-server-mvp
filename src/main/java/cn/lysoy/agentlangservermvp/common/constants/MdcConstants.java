package cn.lysoy.agentlangservermvp.common.constants;

/**
 * MDC 与请求头中与链路追踪相关的键名常量。
 */
public final class MdcConstants {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID_KEY = "requestId";

    private MdcConstants() {
    }
}
