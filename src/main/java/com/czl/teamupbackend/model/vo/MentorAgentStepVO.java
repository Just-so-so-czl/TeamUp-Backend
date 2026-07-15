package com.czl.teamupbackend.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorAgentStepVO {
    private String stepType;
    private String toolName;
    private String status;
    private String summary;
}
