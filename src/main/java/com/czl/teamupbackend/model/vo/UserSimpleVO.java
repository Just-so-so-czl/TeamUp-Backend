package com.czl.teamupbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSimpleVO {

    private Long id;

    private String email;

    private String username;

    private Integer gender;

    private Integer avatar;
}
