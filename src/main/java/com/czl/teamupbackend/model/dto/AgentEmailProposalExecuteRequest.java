package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class AgentEmailProposalExecuteRequest {
    private Long draftId;
    private String subject;
    private String content;
}
