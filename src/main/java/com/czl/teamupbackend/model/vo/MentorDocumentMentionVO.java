package com.czl.teamupbackend.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorDocumentMentionVO {
    private String documentId;
    private String title;
    private Integer type;
    private String typeName;
    private String fileType;
}
