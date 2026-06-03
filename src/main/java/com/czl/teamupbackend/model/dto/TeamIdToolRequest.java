package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class TeamIdToolRequest {

    @JsonProperty(required = true)
    @JsonPropertyDescription("The ID of the team to query task lists for")
    private Long teamId;
}
