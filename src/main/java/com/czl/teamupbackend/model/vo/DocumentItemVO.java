package com.czl.teamupbackend.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentItemVO {

    private Long documentId;

    private Long teamId;

    private String title;

    private Integer type;

    private String typeName;

    private String storagePath;

    private String fileType;

    private Long fileSize;

    private Long creatorId;

    private String creatorName;

    private LocalDateTime createTime;
}

