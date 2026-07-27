package com.czl.teamupbackend.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentCollaborationDocumentPatchBlockVO {
    private String type;
    private String text;
    private Integer level;
    private List<String> items;
    private Integer start;
    private String language;
    private List<String> headers;
    private List<List<String>> rows;
    private String alt;
    private String title;
    private String previewObjectKey;
}
