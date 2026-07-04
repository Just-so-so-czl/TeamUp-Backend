package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class TeamIdToolRequest {
    @JsonPropertyDescription("Optional team ID. The server injects the current team context, so the model should normally leave this empty.")
    private Long teamId;
}
