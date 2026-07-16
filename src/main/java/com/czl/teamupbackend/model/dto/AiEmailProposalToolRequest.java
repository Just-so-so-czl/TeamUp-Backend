package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class AiEmailProposalToolRequest {
    @JsonPropertyDescription("Required recipient member user ID obtained from queryTeamMembers.")
    private Long recipientUserId;
    @JsonPropertyDescription("Short email subject to propose.")
    private String subject;
    @JsonPropertyDescription("Complete plain-text email body to propose.")
    private String content;
}
