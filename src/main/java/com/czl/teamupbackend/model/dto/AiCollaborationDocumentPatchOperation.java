package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
public class AiCollaborationDocumentPatchOperation {

    @JsonPropertyDescription("INSERT_AFTER, REPLACE_BLOCK, or DELETE_BLOCK.")
    private String operation;

    @JsonPropertyDescription("Exact target blockId returned by queryCurrentCollaborationDocument.")
    private String targetBlockId;

    @JsonPropertyDescription("Exact target textHash returned by queryCurrentCollaborationDocument.")
    private String expectedTextHash;

    @JsonPropertyDescription("New Chinese text. Required except DELETE_BLOCK. REPLACE_BLOCK must contain one paragraph or heading only.")
    private String newText;

    @JsonPropertyDescription("Short reason for this edit.")
    private String reason;
}
