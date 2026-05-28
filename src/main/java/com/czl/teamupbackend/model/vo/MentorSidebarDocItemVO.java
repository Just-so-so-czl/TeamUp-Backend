package com.czl.teamupbackend.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorSidebarDocItemVO {

    private String documentId;

    private String title;

    private String creatorName;

    private String dateLabel;

    private String fileType;
}

