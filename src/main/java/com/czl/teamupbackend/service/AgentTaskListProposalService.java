package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.dto.AiTaskListProposalToolRequest;
import com.czl.teamupbackend.model.vo.AgentTaskListProposalVO;
import java.util.Collection;
import java.util.Map;

public interface AgentTaskListProposalService {
    AgentTaskListProposalVO create(Long runId, Long userId, Long teamId, AiTaskListProposalToolRequest request);
    AgentTaskListProposalVO getPending(Long userId, Long runId);
    AgentTaskListProposalVO execute(Long userId, Long draftId, String title, String description, String deadline, java.util.List<String> taskDescriptions);
    Map<Long, AgentTaskListProposalVO> findByRunIds(Long userId, Collection<Long> runIds);
}
