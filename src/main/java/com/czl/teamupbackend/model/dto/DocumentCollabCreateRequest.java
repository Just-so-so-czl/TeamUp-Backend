package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class DocumentCollabCreateRequest {

    private Long teamId;

    private String title;
}
