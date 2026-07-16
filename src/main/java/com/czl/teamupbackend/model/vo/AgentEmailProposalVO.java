package com.czl.teamupbackend.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentEmailProposalVO {
    private String draftId;
    private String runId;
    private String status;
    private String recipientUserId;
    private String recipientName;
    private String recipientEmail;
    private String subject;
    private String content;
    private String resultSummary;
}
