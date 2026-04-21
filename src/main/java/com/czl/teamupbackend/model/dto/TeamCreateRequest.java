package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class TeamCreateRequest {

    private Long userId;

    private String name;

    private String description;
}

