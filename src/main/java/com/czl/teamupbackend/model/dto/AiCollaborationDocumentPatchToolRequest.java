package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.Data;

@Data
public class AiCollaborationDocumentPatchToolRequest {

    @JsonPropertyDescription("snapshotId returned by queryCurrentCollaborationDocument in this agent run.")
    private String snapshotId;

    @JsonPropertyDescription("One complete proposal containing 1 to 24 operations against the snapshot. Include every requested document change in this single array.")
    private List<AiCollaborationDocumentPatchOperation> operations;
}
