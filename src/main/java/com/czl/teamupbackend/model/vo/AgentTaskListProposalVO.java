package com.czl.teamupbackend.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentTaskListProposalVO {
    private String draftId;
    private String runId;
    private String status;
    private String title;
    private String description;
    private String deadline;
    private List<String> taskDescriptions;
    private String resultSummary;
}
