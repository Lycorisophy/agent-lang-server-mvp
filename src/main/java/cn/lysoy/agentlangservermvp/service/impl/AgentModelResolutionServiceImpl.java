package cn.lysoy.agentlangservermvp.service.impl;

import cn.lysoy.agentlangservermvp.common.constants.ErrorCodeConstants;
import cn.lysoy.agentlangservermvp.common.constants.MessageConstants;
import cn.lysoy.agentlangservermvp.common.exception.BusinessException;
import cn.lysoy.agentlangservermvp.model.ModelRegistry;
import cn.lysoy.agentlangservermvp.service.IAgentModelResolutionService;
import org.springframework.stereotype.Service;

/**
 * {@link IAgentModelResolutionService} 占位实现，待智能体链路落地。
 */
@Service
public class AgentModelResolutionServiceImpl implements IAgentModelResolutionService {

    @Override
    public ModelRegistry resolve(Long modelId, String modelCode) {
        throw new BusinessException(
                ErrorCodeConstants.AGENT_MODEL_RESOLUTION_RESERVED,
                MessageConstants.AGENT_MODEL_RESOLUTION_RESERVED
        );
    }
}
