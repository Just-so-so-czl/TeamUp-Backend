package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class MentorDocumentMentionSearchRequest {
    private Long teamId;
    private String keyword;
}
