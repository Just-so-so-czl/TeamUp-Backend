package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.Data;

@Data
public class AiCollaborationDocumentPatchToolRequest {

    @JsonPropertyDescription("snapshotId returned by queryCurrentCollaborationDocument in this agent run.")
    private String snapshotId;

    @JsonPropertyDescription("One to twelve paragraph/section patch operations against that snapshot.")
    private List<AiCollaborationDocumentPatchOperation> operations;
}
