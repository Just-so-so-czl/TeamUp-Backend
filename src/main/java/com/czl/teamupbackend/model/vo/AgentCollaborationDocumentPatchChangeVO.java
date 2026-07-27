package com.czl.teamupbackend.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentCollaborationDocumentPatchChangeVO {
    private String operation;
    private String targetBlockId;
    private String destinationBlockId;
    private String beforeText;
    private String afterText;
    private String fromPositionLabel;
    private String toPositionLabel;
    private String fromPreviousLabel;
    private String fromNextLabel;
    private String toPreviousLabel;
    private String toNextLabel;
    private List<AgentCollaborationDocumentPatchBlockVO> beforeBlocks;
    private List<AgentCollaborationDocumentPatchBlockVO> afterBlocks;
    private String reason;
}
