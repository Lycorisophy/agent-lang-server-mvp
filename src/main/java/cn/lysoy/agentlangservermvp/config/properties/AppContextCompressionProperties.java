package cn.lysoy.agentlangservermvp.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * version0.3：同步上下文上限与异步压缩可调参数。
 */
@Getter
@ConfigurationProperties(prefix = "app")
public class AppContextCompressionProperties {

    private final ContextCfg context = new ContextCfg();
    private final CompressCfg compress = new CompressCfg();

    @Setter
    @Getter
    public static class ContextCfg {
        /** 单次请求发往大模型的上下文 token 上限（粗估算）。
         * TODO 考虑改成百分比，最大值放mysql
         * */
        private int maxTokens = 64000;

    }

    @Getter
    public static class CompressCfg {
        @Setter
        private boolean enabled = false;
        /** 为 true 且已暴露 StringRedisTemplate 时使用 Redis 分布式锁；否则使用进程内锁。 */
        @Setter
        private boolean useRedis = false;
        @Setter
        private int triggerThresholdTokens = 10000;
        /** 压缩后若不达此节省比例（0–1），回滚本轮摘要写入。 */
        @Setter
        private double minSavedTokenRatio = 0.3;
        @Setter
        private int preserveRecentRounds = 4;
        @Setter
        private int summaryBatchSize = 6;
        @Setter
        private int lockTtlSeconds = 30;
        @Setter
        private int toolReplyMaxChars = 500;
        @Setter
        private boolean deterministicRulesEnabled = false;
        /** 非空时压缩解析固定使用该模型代码，否则按 {@link cn.lysoy.agentlangservermvp.service.ICompressionModelResolutionService} 默认选取。 */
        private String compressionModelCode = "";
        // 摘要阶段总超时（秒），默认120
        @Setter
        private int summaryTotalTimeoutSeconds = 120;

        public void setCompressionModelCode(String compressionModelCode) {
            this.compressionModelCode = compressionModelCode != null ? compressionModelCode : "";
        }
    }
}
