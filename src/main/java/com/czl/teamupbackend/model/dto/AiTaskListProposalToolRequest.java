package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.Data;

@Data
public class AiTaskListProposalToolRequest {
    @JsonPropertyDescription("Task list title.")
    private String title;
    @JsonPropertyDescription("Task list description.")
    private String description;
    @JsonPropertyDescription("Subtask descriptions only. Do not include deadline or assignee.")
    private List<String> taskDescriptions;
}
