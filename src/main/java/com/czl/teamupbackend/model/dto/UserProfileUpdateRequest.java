package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {

    private String email;

    private String username;

    private Integer avatar;
}

