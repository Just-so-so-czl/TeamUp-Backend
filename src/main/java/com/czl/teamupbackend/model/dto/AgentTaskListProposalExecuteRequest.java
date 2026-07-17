package com.czl.teamupbackend.model.dto;

import java.util.List;
import lombok.Data;

@Data
public class AgentTaskListProposalExecuteRequest {
    private Long draftId;
    private String title;
    private String description;
    private String deadline;
    private List<String> taskDescriptions;
}
