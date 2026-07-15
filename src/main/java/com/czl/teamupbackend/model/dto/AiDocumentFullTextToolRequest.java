package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class AiDocumentFullTextToolRequest {

    @JsonPropertyDescription("Required document ID obtained from queryTeamDocuments. Do not guess IDs.")
    private Long documentId;
}
