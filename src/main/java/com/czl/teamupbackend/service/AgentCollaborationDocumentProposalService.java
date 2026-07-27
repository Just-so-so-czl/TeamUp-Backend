package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.dto.AiCollaborationDocumentPatchToolRequest;
import com.czl.teamupbackend.model.vo.AgentCollaborationDocumentPatchProposalVO;
import java.util.Collection;
import java.util.Map;

public interface AgentCollaborationDocumentProposalService {
    Map<String, Object> captureForAgent(Long runId, Long userId, Long teamId, Long documentId);
    AgentCollaborationDocumentPatchProposalVO create(Long runId, Long userId, Long teamId, Long documentId,
                                                      AiCollaborationDocumentPatchToolRequest request);
    AgentCollaborationDocumentPatchProposalVO getPending(Long userId, Long runId);
    boolean hasExecuted(Long userId, Long runId);
    AgentCollaborationDocumentPatchProposalVO execute(Long userId, Long draftId);
    Map<Long, AgentCollaborationDocumentPatchProposalVO> findByRunIds(Long userId, Collection<Long> runIds);
}
