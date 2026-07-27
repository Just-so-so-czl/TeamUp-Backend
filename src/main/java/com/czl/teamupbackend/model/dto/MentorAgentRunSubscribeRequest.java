package com.czl.teamupbackend.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MentorAgentRunSubscribeRequest {

    @NotNull(message = "runId不能为空")
    private Long runId;
}
