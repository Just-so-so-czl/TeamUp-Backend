package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.Data;

@Data
public class AiCollaborationDocumentPatchOperation {

    @JsonPropertyDescription("INSERT_BEFORE, INSERT_AFTER, REPLACE_BLOCK, DELETE_BLOCK, MOVE_IMAGE_BEFORE, or MOVE_IMAGE_AFTER.")
    private String operation;

    @JsonPropertyDescription("Exact target blockId returned by queryCurrentCollaborationDocument.")
    private String targetBlockId;

    @JsonPropertyDescription("Exact target textHash returned by queryCurrentCollaborationDocument.")
    private String expectedTextHash;

    @JsonPropertyDescription("Exact destination blockId returned by queryCurrentCollaborationDocument. Required only by MOVE_IMAGE_BEFORE and MOVE_IMAGE_AFTER.")
    private String destinationBlockId;

    @JsonPropertyDescription("Exact destination textHash returned by queryCurrentCollaborationDocument. Required only by MOVE_IMAGE_BEFORE and MOVE_IMAGE_AFTER.")
    private String expectedDestinationTextHash;

    @JsonPropertyDescription("Deprecated compatibility field for old single-text proposals. New proposals must use newBlocks instead.")
    private String newText;

    @JsonPropertyDescription("All blocks inserted or used to replace the target. REPLACE_BLOCK may contain multiple blocks. Omit for DELETE_BLOCK and image move operations.")
    private List<AiCollaborationDocumentPatchBlock> newBlocks;

    @JsonPropertyDescription("Short reason for this edit.")
    private String reason;
}
