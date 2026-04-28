package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class TeamUpdateRequest {

    private Long teamId;

    private String name;

    private String description;
}

