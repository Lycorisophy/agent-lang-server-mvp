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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * version0.3：异步压缩（工具过长清理、摘要分批）。压缩未达标时抛出异常以使事务回滚。
 */
@Service
public class InnerMessageCompressServiceImpl implements InnerMessageCompressService {

    private static final Logger log = LogManager.getLogger(InnerMessageCompressServiceImpl.class);

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
            log.info(
                    "compress_run_start sessionId={} thresholdTokens={} useRedisLock={}",
                    sid,
                    cfg.getTriggerThresholdTokens(),
                    cfg.isUseRedis() && redisTemplateProvider.getIfAvailable() != null
            );
            List<InnerMessage> allOrdered = selectSessionMessages(sid);
            int totalRough = sumRoughTokens(allOrdered);
            log.debug("compress_precheck_token_sum sessionId={} totalRoughApprox={}", sid, totalRough);
            if (totalRough <= cfg.getTriggerThresholdTokens()) {
                log.info(
                        "compress_skip_below_threshold sessionId={} totalRoughApprox={} threshold={}",
                        sid,
                        totalRough,
                        cfg.getTriggerThresholdTokens()
                );
                return;
            }
            try {
                transactionTemplate.executeWithoutResult(ts -> runCompressionInTransaction(sid, cfg));
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
                                             AppContextCompressionProperties.CompressCfg cfg) {
        log.info("compress_tx_begin sessionId={}", sessionId);
        pipelineDropLongTools(sessionId, cfg);
        deterministicCleanupPlaceholder(cfg, sessionId);

        List<InnerMessage> eligibleOrdered = filterNoneUncompressed(selectSessionMessages(sessionId));
        List<List<InnerMessage>> rounds = splitUserRounds(eligibleOrdered);
        int preserve = cfg.getPreserveRecentRounds();
        log.info(
                "compress_round_split sessionId={} eligibleRows={} roundCount={} preserveRecentRounds={}",
                sessionId,
                eligibleOrdered.size(),
                rounds.size(),
                preserve
        );

        if (rounds.size() <= preserve || rounds.isEmpty()) {
            log.info("compress_skip_recent_tail_protected session={} rounds={}", sessionId, rounds.size());
            return;
        }

        ModelRegistry compressor = resolveCompressionModel();
        log.info(
                "compress_compressor_model modelCode={} provider={}",
                compressor.getModelCode(),
                compressor.getProvider()
        );
        List<List<InnerMessage>> toCompressRoundGroups = rounds.subList(0, rounds.size() - preserve);
        List<InnerMessage> toCompressFlatten = flattenRounds(toCompressRoundGroups);
        int batchSize = Math.max(1, cfg.getSummaryBatchSize());

        log.info(
                "compress_pipe3_plan sessionId={} rowsToFlatten={} batchSize={} minSavedRatio={}",
                sessionId,
                toCompressFlatten.size(),
                batchSize,
                cfg.getMinSavedTokenRatio()
        );

        int prePipe3 = sumRoughTokens(selectSessionMessages(sessionId));
        log.info("compress_pre_pipe3_tokens sessionId={} sumRoughApprox={}", sessionId, prePipe3);

        boolean ranPipe3 = false;
        for (int i = 0; i < toCompressFlatten.size(); i += batchSize) {
            int end = Math.min(i + batchSize, toCompressFlatten.size());
            List<InnerMessage> batch = toCompressFlatten.subList(i, end);
            if (batch.isEmpty()) {
                continue;
            }
            ranPipe3 = true;
            int originalLenSum = batch.stream().mapToInt(m -> ContentMetrics.charLength(m.getContent())).sum();
            log.debug(
                    "compress_batch_summarize_start sessionId={} batchIndex={}-{} msgs={} charSum={}",
                    sessionId,
                    i,
                    end - 1,
                    batch.size(),
                    originalLenSum
            );
            String summaryText = summarizeBatch(compressor, batch);
            log.debug(
                    "compress_batch_summarize_done sessionId={} summaryChars={}",
                    sessionId,
                    summaryText.length()
            );

            InnerMessage summaryRow = new InnerMessage();
            summaryRow.setSessionId(sessionId);
            summaryRow.setRole(ChatConstants.ROLE_SYSTEM);
            summaryRow.setContent(summaryText);
            summaryRow.setContentLength(ContentMetrics.charLength(summaryText));
            summaryRow.setCompressedLength(originalLenSum);
            summaryRow.setCompressMethod(CompressMethodConstants.SUMMARY);
            summaryRow.setTokenCount(ContentMetrics.roughTokenEstimate(summaryText));
            summaryRow.setDelFlag(0);
            innerMessageMapper.insert(summaryRow);

            for (InnerMessage row : batch) {
                innerMessageMapper.deleteById(row.getId());
            }
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

    private ModelRegistry resolveCompressionModel() {
        String override = properties.getCompress().getCompressionModelCode();
        String code = override != null && !override.isBlank() ? override.trim() : null;
        return compressionModelResolutionService.resolve(code);
    }

    private String summarizeBatch(ModelRegistry compressor, List<InnerMessage> batch) {
        log.debug(
                "summarize_batch_llm_call compressionModel={} batchSize={}",
                compressor.getModelCode(),
                batch.size()
        );
        StringBuilder body = new StringBuilder();
        for (InnerMessage m : batch) {
            body.append("[").append(m.getRole()).append("]\n").append(m.getContent()).append("\n\n");
        }
        List<ChatMessage> prompt = List.of(
                SystemMessage.from(
                        "将下列对话段落压缩成一条简体中文摘要：保留实体、时间与关键动作；不要发散添加信息。"),
                UserMessage.from(body.toString())
        );
        try {
            return langChainChatModelFactory.createSync(compressor).generate(prompt).content().text();
        } catch (Exception e) {
            throw new IllegalStateException("summarize_batch_failed", e);
        }
    }

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

    /**
     * 以 user 消息锚定「轮」：同一轮含该 user 之后至下一条 user 之前的所有消息。
     */
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

    private void pipelineDropLongTools(String sessionId, AppContextCompressionProperties.CompressCfg cfg) {
        List<InnerMessage> tools = innerMessageMapper.selectList(
                new LambdaQueryWrapper<InnerMessage>()
                        .eq(InnerMessage::getSessionId, sessionId)
                        .eq(InnerMessage::getRole, ChatConstants.ROLE_TOOL)
        );
        for (InnerMessage t : tools) {
            Integer len = t.getContentLength();
            int cmp = len != null ? len : ContentMetrics.charLength(t.getContent());
            if (cmp > cfg.getToolReplyMaxChars()) {
                innerMessageMapper.deleteById(t.getId());
                log.info("compress_pipeline1_logical_delete_tool session={} id={}", sessionId, t.getId());
            }
        }
    }

    private void deterministicCleanupPlaceholder(AppContextCompressionProperties.CompressCfg cfg, String sessionId) {
        if (cfg.isDeterministicRulesEnabled()) {
            log.debug("compress_pipeline2_not_implemented session={}", sessionId);
        }
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
