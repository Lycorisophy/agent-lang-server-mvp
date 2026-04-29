package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import cn.lysoy.agentlangservermvp.common.constants.CompressMethodConstants;
import cn.lysoy.agentlangservermvp.config.properties.AppContextCompressionProperties;
import cn.lysoy.agentlangservermvp.integration.LangChainChatModelFactory;
import cn.lysoy.agentlangservermvp.mapper.InnerMessageMapper;
import cn.lysoy.agentlangservermvp.model.InnerMessage;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.ICompressionModelResolutionService;
import cn.lysoy.agentlangservermvp.service.InnerMessageCompressService;
import cn.lysoy.agentlangservermvp.util.ContentMetrics;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * version0.4：异步压缩（工具过长清理、摘要分批）。
 * 优化：复用查询、批量删除、提示词增强、摘要阶段全局超时控制（不中断任务）。
 */
@Service
public class InnerMessageCompressServiceImpl implements InnerMessageCompressService {

    private static final Logger log = LogManager.getLogger(InnerMessageCompressServiceImpl.class);

    /**
     * 优化后的压缩提示词：保留关键信息，限制长度，减少发散
     */
    private static final String SUMMARY_SYSTEM_PROMPT =
            "你是一个专业对话摘要引擎。将以下多轮对话压缩成一段中文摘要（不超过300字），" +
                    "必须保留所有关键实体（人名、组织、地点）、日期、数字和决策，不得添加原文未出现的信息。";

    private final AppContextCompressionProperties properties;
    private final InnerMessageMapper innerMessageMapper;
    private final ICompressionModelResolutionService compressionModelResolutionService;
    private final LangChainChatModelFactory langChainChatModelFactory;
    private final TransactionTemplate transactionTemplate;
    private final org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    private final ConcurrentHashMap<String, ReentrantLock> localSessionLocks = new ConcurrentHashMap<>();

    public InnerMessageCompressServiceImpl(AppContextCompressionProperties properties,
                                           InnerMessageMapper innerMessageMapper,
                                           ICompressionModelResolutionService compressionModelResolutionService,
                                           LangChainChatModelFactory langChainChatModelFactory,
                                           PlatformTransactionManager transactionManager,
                                           org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = properties;
        this.innerMessageMapper = innerMessageMapper;
        this.compressionModelResolutionService = compressionModelResolutionService;
        this.langChainChatModelFactory = langChainChatModelFactory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    public void compressIfNeeded(String sessionId) {
        if (!properties.getCompress().isEnabled()) {
            log.trace("compress_skipped_disabled sessionId={}", sessionId);
            return;
        }
        AppContextCompressionProperties.CompressCfg cfg = properties.getCompress();
        String sid = Objects.requireNonNull(sessionId).trim();

        Runnable locked = () -> {
            log.info("compress_run_start sessionId={} thresholdTokens={} useRedisLock={}",
                    sid, cfg.getTriggerThresholdTokens(),
                    cfg.isUseRedis() && redisTemplateProvider.getIfAvailable() != null);

            List<InnerMessage> allOrdered = selectSessionMessages(sid);
            int totalRough = sumRoughTokens(allOrdered);
            log.debug("compress_precheck_token_sum sessionId={} totalRoughApprox={}", sid, totalRough);

            if (totalRough <= cfg.getTriggerThresholdTokens()) {
                log.info("compress_skip_below_threshold sessionId={} totalRoughApprox={} threshold={}",
                        sid, totalRough, cfg.getTriggerThresholdTokens());
                return;
            }

            try {
                transactionTemplate.executeWithoutResult(ts -> runCompressionInTransaction(sid, cfg, allOrdered));
            } catch (IllegalStateException ex) {
                log.warn("compress_rolled_back session={}: {}", sid, ex.getMessage());
            } catch (DataAccessException ex) {
                log.warn("compress_transaction_failed session={}", sid, ex);
            } catch (RuntimeException ex) {
                log.warn("compress_failed session={}", sid, ex);
            }
        };

        withSessionLock(cfg, sid, locked);
    }

    private void runCompressionInTransaction(String sessionId,
                                             AppContextCompressionProperties.CompressCfg cfg,
                                             List<InnerMessage> allOrdered) {
        log.info("compress_tx_begin sessionId={}", sessionId);

        pipelineDropLongTools(allOrdered, cfg, sessionId);
        deterministicCleanupPlaceholder(cfg, sessionId);

        int prePipe3 = sumRoughTokens(allOrdered);
        log.info("compress_pre_pipe3_tokens sessionId={} sumRoughApprox={}", sessionId, prePipe3);

        List<InnerMessage> eligibleOrdered = filterNoneUncompressed(allOrdered);
        List<List<InnerMessage>> rounds = splitUserRounds(eligibleOrdered);
        int preserve = cfg.getPreserveRecentRounds();
        log.info("compress_round_split sessionId={} eligibleRows={} roundCount={} preserveRecentRounds={}",
                sessionId, eligibleOrdered.size(), rounds.size(), preserve);

        if (rounds.size() <= preserve || rounds.isEmpty()) {
            log.info("compress_skip_recent_tail_protected session={} rounds={}", sessionId, rounds.size());
            return;
        }

        ModelRegistry compressor = resolveCompressionModel();
        log.info("compress_compressor_model modelCode={} provider={}",
                compressor.getModelCode(), compressor.getProvider());

        List<List<InnerMessage>> toCompressRoundGroups = rounds.subList(0, rounds.size() - preserve);
        List<InnerMessage> toCompressFlatten = flattenRounds(toCompressRoundGroups);
        int batchSize = Math.max(1, cfg.getSummaryBatchSize());

        // 摘要阶段总超时（秒），配置缺省 120 秒
        int totalTimeoutSec = getSummaryTotalTimeoutSeconds(cfg);
        long deadlineMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(totalTimeoutSec);
        // 安全缓冲时间：如果剩余不足 5 秒就不再开始新批次
        long safeBufferMs = TimeUnit.SECONDS.toMillis(5);

        log.info("compress_pipe3_plan sessionId={} rowsToFlatten={} batchSize={} minSavedRatio={} totalTimeout={}s",
                sessionId, toCompressFlatten.size(), batchSize, cfg.getMinSavedTokenRatio(), totalTimeoutSec);

        boolean ranPipe3 = false;
        List<Long> batchDeleteIds = new ArrayList<>();
        for (int i = 0; i < toCompressFlatten.size(); i += batchSize) {
            // ---------- 超时检查 ----------
            long remaining = deadlineMillis - System.currentTimeMillis();
            if (remaining < safeBufferMs) {
                log.info("compress_pipe3_timeout_break sessionId={} remainingMs={} processedBatches={}",
                        sessionId, remaining, i / batchSize);
                break;  // 不再开始新批次，已提交的任务会正常执行完成
            }

            int end = Math.min(i + batchSize, toCompressFlatten.size());
            List<InnerMessage> batch = toCompressFlatten.subList(i, end);
            if (batch.isEmpty()) {
                continue;
            }
            ranPipe3 = true;
            int originalLenSum = batch.stream().mapToInt(m -> ContentMetrics.charLength(m.getContent())).sum();
            log.debug("compress_batch_summarize_start sessionId={} batchIndex={}-{} msgs={} charSum={}",
                    sessionId, i, end - 1, batch.size(), originalLenSum);

            // 同步调用 LLM 生成摘要
            String summaryText = summarizeBatch(compressor, batch);
            log.debug("compress_batch_summarize_done sessionId={} summaryChars={}",
                    sessionId, summaryText.length());

            InnerMessage summaryRow = buildSummaryRow(sessionId, summaryText, originalLenSum);
            innerMessageMapper.insert(summaryRow);
            batchDeleteIds.addAll(batch.stream().map(InnerMessage::getId).collect(Collectors.toList()));
        }

        if (!batchDeleteIds.isEmpty()) {
            innerMessageMapper.deleteBatchIds(batchDeleteIds);
            log.debug("compress_batch_delete_ids sessionId={} count={}", sessionId, batchDeleteIds.size());
        }

        if (!ranPipe3) {
            log.info("compress_pipe3_nop sessionId={}", sessionId);
            return;
        }

        int postPipe3 = sumRoughTokens(selectSessionMessages(sessionId));
        double savedRatio = prePipe3 > 0 ? (double) (prePipe3 - postPipe3) / (double) prePipe3 : 0.0;
        log.info("compress_pipe3_done session={} preTokensRough={} postTokensRough={} savedRatio={}",
                sessionId, prePipe3, postPipe3, savedRatio);

        if (savedRatio < cfg.getMinSavedTokenRatio()) {
            throw new IllegalStateException("compression_saved_below_threshold");
        }
    }

    /**
     * 解析压缩模型（根据配置）
     */
    private ModelRegistry resolveCompressionModel() {
        String override = properties.getCompress().getCompressionModelCode();
        String code = override != null && !override.isBlank() ? override.trim() : null;
        return compressionModelResolutionService.resolve(code);
    }

    /**
     * 同步调用 LLM 生成摘要（使用优化后的提示词）
     */
    private String summarizeBatch(ModelRegistry compressor, List<InnerMessage> batch) {
        log.debug("summarize_batch_llm_call compressionModel={} batchSize={}",
                compressor.getModelCode(), batch.size());
        StringBuilder body = new StringBuilder();
        for (InnerMessage m : batch) {
            body.append("[").append(m.getRole()).append("]\n").append(m.getContent()).append("\n\n");
        }
        List<ChatMessage> prompt = List.of(
                SystemMessage.from(SUMMARY_SYSTEM_PROMPT),
                UserMessage.from(body.toString())
        );
        return langChainChatModelFactory.createSync(compressor).generate(prompt).content().text();
    }

    private InnerMessage buildSummaryRow(String sessionId, String summaryText, int originalLenSum) {
        InnerMessage summaryRow = new InnerMessage();
        summaryRow.setSessionId(sessionId);
        summaryRow.setRole(ChatConstants.ROLE_SYSTEM);
        summaryRow.setContent(summaryText);
        summaryRow.setContentLength(ContentMetrics.charLength(summaryText));
        summaryRow.setCompressedLength(originalLenSum);
        summaryRow.setCompressMethod(CompressMethodConstants.SUMMARY);
        summaryRow.setTokenCount(ContentMetrics.roughTokenEstimate(summaryText));
        summaryRow.setDelFlag(0);
        return summaryRow;
    }

    private void pipelineDropLongTools(List<InnerMessage> allOrdered,
                                       AppContextCompressionProperties.CompressCfg cfg,
                                       String sessionId) {
        List<Long> idsToDelete = new ArrayList<>();
        Iterator<InnerMessage> it = allOrdered.iterator();
        while (it.hasNext()) {
            InnerMessage msg = it.next();
            if (ChatConstants.ROLE_TOOL.equalsIgnoreCase(msg.getRole())) {
                int len = ContentMetrics.charLength(msg.getContent());
                if (len > cfg.getToolReplyMaxChars()) {
                    idsToDelete.add(msg.getId());
                    it.remove();
                    log.info("compress_pipeline1_logical_delete_tool session={} id={}", sessionId, msg.getId());
                }
            }
        }
        if (!idsToDelete.isEmpty()) {
            innerMessageMapper.deleteBatchIds(idsToDelete);
            log.debug("compress_pipeline1_delete_tool_batch session={} count={}", sessionId, idsToDelete.size());
        }
    }

    private void deterministicCleanupPlaceholder(AppContextCompressionProperties.CompressCfg cfg, String sessionId) {
        if (cfg.isDeterministicRulesEnabled()) {
            log.debug("compress_pipeline2_not_implemented session={}", sessionId);
        }
    }

    /**
     * 从配置获取摘要阶段总超时（秒），默认120秒
     */
    private int getSummaryTotalTimeoutSeconds(AppContextCompressionProperties.CompressCfg cfg) {
        try {
            return cfg.getSummaryTotalTimeoutSeconds();
        } catch (Exception e) {
            return 120;
        }
    }

    // ==================== 工具方法 ====================

    private List<InnerMessage> selectSessionMessages(String sessionId) {
        return innerMessageMapper.selectList(
                new LambdaQueryWrapper<InnerMessage>()
                        .eq(InnerMessage::getSessionId, sessionId)
                        .orderByAsc(InnerMessage::getCreateAt)
        );
    }

    private List<InnerMessage> filterNoneUncompressed(List<InnerMessage> chronological) {
        List<InnerMessage> out = new ArrayList<>();
        for (InnerMessage m : chronological) {
            String cm = m.getCompressMethod();
            if (cm == null || CompressMethodConstants.NONE.equalsIgnoreCase(cm)) {
                out.add(m);
            }
        }
        return out;
    }

    static List<List<InnerMessage>> splitUserRounds(List<InnerMessage> eligibleOrdered) {
        List<List<InnerMessage>> rounds = new ArrayList<>();
        List<InnerMessage> buffer = new ArrayList<>();
        for (InnerMessage m : eligibleOrdered) {
            if (ChatConstants.ROLE_USER.equalsIgnoreCase(m.getRole()) && !buffer.isEmpty()) {
                rounds.add(new ArrayList<>(buffer));
                buffer.clear();
            }
            buffer.add(m);
        }
        if (!buffer.isEmpty()) {
            rounds.add(buffer);
        }
        return rounds;
    }

    private static List<InnerMessage> flattenRounds(List<List<InnerMessage>> roundsSubset) {
        List<InnerMessage> flat = new ArrayList<>();
        for (List<InnerMessage> r : roundsSubset) {
            flat.addAll(r);
        }
        return flat;
    }

    static int sumRoughTokens(List<InnerMessage> rows) {
        int s = 0;
        for (InnerMessage m : rows) {
            Integer t = m.getTokenCount();
            if (t != null && t > 0) {
                s += t;
            } else {
                s += ContentMetrics.roughTokenEstimate(m.getContent());
            }
        }
        return s;
    }

    private void withSessionLock(AppContextCompressionProperties.CompressCfg cfg, String sessionId, Runnable runnable) {
        String redisKey = "compress:lock:" + sessionId;
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (cfg.isUseRedis() && redis != null) {
            Boolean ok = redis.opsForValue().setIfAbsent(redisKey, "1",
                    Duration.ofSeconds(Math.max(1L, cfg.getLockTtlSeconds())));
            if (!Boolean.TRUE.equals(ok)) {
                log.info("compress_lock_busy session={} backend=redis", sessionId);
                return;
            }
            try {
                log.debug("compress_lock_acquired session={} backend=redis", sessionId);
                runnable.run();
            } finally {
                redis.delete(redisKey);
                log.debug("compress_lock_released session={} backend=redis", sessionId);
            }
            return;
        }
        ReentrantLock lock = localSessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        try {
            if (!lock.tryLock(Math.max(1L, cfg.getLockTtlSeconds()), TimeUnit.SECONDS)) {
                log.info("compress_lock_busy session={} backend=local", sessionId);
                return;
            }
            log.debug("compress_lock_acquired session={} backend=local", sessionId);
            runnable.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("compress_lock_interrupted session={} backend=local", sessionId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("compress_lock_released session={} backend=local", sessionId);
            }
        }
    }
}