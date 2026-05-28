package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class MentorCreateSessionRequest {

    private Long teamId;

    private String title;
}

