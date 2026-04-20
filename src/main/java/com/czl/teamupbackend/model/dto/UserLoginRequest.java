package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class UserLoginRequest {

    private String email;

    private String password;
}
