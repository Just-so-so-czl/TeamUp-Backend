package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.dto.AiEmailProposalToolRequest;
import com.czl.teamupbackend.model.vo.AgentEmailProposalVO;
import java.util.Collection;
import java.util.Map;

public interface AgentEmailProposalService {
    AgentEmailProposalVO create(Long runId, Long operatorUserId, Long teamId, AiEmailProposalToolRequest request);
    AgentEmailProposalVO getPending(Long operatorUserId, Long runId);
    boolean hasExecuted(Long operatorUserId, Long runId);
    AgentEmailProposalVO execute(Long operatorUserId, Long draftId, String subject, String content);
    Map<Long, AgentEmailProposalVO> findByRunIds(Long operatorUserId, Collection<Long> runIds);
}
