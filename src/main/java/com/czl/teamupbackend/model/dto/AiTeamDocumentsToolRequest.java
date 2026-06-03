package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class AiTeamDocumentsToolRequest {

    @JsonPropertyDescription("The ID of the team to query documents for. Optional because the server injects current team context.")
    private Long teamId;

    @JsonPropertyDescription("Optional document type filter: 1 for resource documents, 3 for agent knowledge documents. Leave empty to query both.")
    private Integer type;
}

