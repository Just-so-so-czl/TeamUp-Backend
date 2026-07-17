package com.czl.teamupbackend.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentCollaborationDocumentPatchProposalVO {
    private String draftId;
    private String runId;
    private String status;
    private String documentId;
    private String changeSummary;
    private List<AgentCollaborationDocumentPatchChangeVO> changes;
    private String resultSummary;
    private String errorMsg;
}
