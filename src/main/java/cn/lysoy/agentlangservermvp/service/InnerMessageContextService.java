package cn.lysoy.agentlangservermvp.service;

import cn.lysoy.agentlangservermvp.common.constants.ChatConstants;
import cn.lysoy.agentlangservermvp.mapper.InnerMessageMapper;
import cn.lysoy.agentlangservermvp.model.InnerMessage;
import cn.lysoy.agentlangservermvp.service.dto.ContextTruncateOutcome;
import cn.lysoy.agentlangservermvp.util.ContentMetrics;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 内表消息的同步截断与上下文窗口控制（不写库）。
 */
@Service
public class InnerMessageContextService {

    private static final Logger log = LogManager.getLogger(InnerMessageContextService.class);

    private final InnerMessageMapper innerMessageMapper;

    public InnerMessageContextService(InnerMessageMapper innerMessageMapper) {
        this.innerMessageMapper = innerMessageMapper;
    }

    /**
     * 按会话加载内表消息后，在满足 {@code maxTokens}（粗略 token）的前提下截断上下文。
     * 策略等价于：反复删掉「剩余列表里时间最早的非 {@code system}」直到总 rough token 不超上限，
     * 即按时间在非 system 子序列上的前缀删除；每条 system 在行内不参与删除候选。
     * <p>
     * 时间复杂度 O(n)，空间 O(n)（与原列表同量级的工作数组与结果列表），无反复全量重算、无 {@link ArrayList#remove} 搬移。
     */
    public ContextTruncateOutcome buildContextWithTruncation(String sessionId, int maxTokens) {
        log.debug("context_truncate_start sessionId={} maxTokens={}", sessionId, maxTokens);

        List<InnerMessage> messages = innerMessageMapper.selectList(
                new LambdaQueryWrapper<InnerMessage>()
                        .eq(InnerMessage::getSessionId, sessionId)
                        .orderByAsc(InnerMessage::getCreateAt)
        );
        int n = messages.size();
        log.debug("context_truncate_loaded sessionId={} innerRows={}", sessionId, n);

        int[] roughTokens = new int[n];
        boolean[] isSystem = new boolean[n];
        int totalTokens = 0;

        for (int i = 0; i < n; i++) {
            InnerMessage msg = messages.get(i);
            int t = normalizeTokenEstimate(msg);
            roughTokens[i] = t;
            isSystem[i] = isSystemRole(msg.getRole());
            totalTokens += t;
        }
        log.debug("context_truncate_tokens_initial sessionId={} sumRoughApprox={}", sessionId, totalTokens);

        if (totalTokens <= maxTokens) {
            log.info(
                    "context_truncate_done sessionId={} retainedRows={} initialRoughApprox={} afterRoughApprox={} droppedMsgs={} droppedTokensApprox={} maxTokens={}",
                    sessionId,
                    n,
                    totalTokens,
                    totalTokens,
                    0,
                    0,
                    maxTokens
            );
            return new ContextTruncateOutcome(new ArrayList<>(messages), totalTokens, 0, 0);
        }

        int currentTotal = totalTokens;
        int deleteCount = 0;
        int droppedTok = 0;

        for (int i = 0; i < n; i++) {
            if (isSystem[i]) {
                continue;
            }
            if (currentTotal <= maxTokens) {
                break;
            }
            currentTotal -= roughTokens[i];
            droppedTok += roughTokens[i];
            deleteCount++;
            log.debug(
                    "context_truncate_drop sessionId={} idx={} role={} droppedTokensApprox={} remainingRoughApprox={}",
                    sessionId,
                    i,
                    messages.get(i).getRole(),
                    roughTokens[i],
                    currentTotal
            );
        }

        if (currentTotal > maxTokens && deleteCount == 0) {
            log.warn(
                    "context_truncate_cannot_trim sessionId={} remainingRoughApprox={} maxTokens={} (messages_are_all_system)",
                    sessionId,
                    currentTotal,
                    maxTokens
            );
            return new ContextTruncateOutcome(new ArrayList<>(messages), totalTokens, 0, 0);
        }

        if (currentTotal > maxTokens && deleteCount > 0) {
            log.warn(
                    "context_truncate_still_over_after_dropping_non_system sessionId={} remainingRoughApprox={} maxTokens={} droppedNonSys={}",
                    sessionId,
                    currentTotal,
                    maxTokens,
                    deleteCount
            );
        }

        List<InnerMessage> retained = new ArrayList<>(Math.max(0, n - deleteCount));
        int nonSysDropped = 0;
        for (int i = 0; i < n; i++) {
            if (isSystem[i]) {
                retained.add(messages.get(i));
            } else {
                if (nonSysDropped < deleteCount) {
                    nonSysDropped++;
                } else {
                    retained.add(messages.get(i));
                }
            }
        }

        log.info(
                "context_truncate_done sessionId={} retainedRows={} initialRoughApprox={} afterRoughApprox={} droppedMsgs={} droppedTokensApprox={} maxTokens={}",
                sessionId,
                retained.size(),
                totalTokens,
                currentTotal,
                deleteCount,
                droppedTok,
                maxTokens
        );

        return new ContextTruncateOutcome(retained, totalTokens, deleteCount, droppedTok);
    }

    private static boolean isSystemRole(String role) {
        return ChatConstants.ROLE_SYSTEM.equalsIgnoreCase(role);
    }

    private static int normalizeTokenEstimate(InnerMessage m) {
        Integer t = m.getTokenCount();
        if (t != null && t > 0) {
            return t;
        }
        return ContentMetrics.roughTokenEstimate(m.getContent());
    }
}
