package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class UserRegisterRequest {

    private String email;

    private String username;

    private String password;

    private String confirmPassword;

    /**
     * 1-male, 2-female
     */
    private Integer gender;

    /**
     * 1-8
     */
    private Integer avatar;
}
