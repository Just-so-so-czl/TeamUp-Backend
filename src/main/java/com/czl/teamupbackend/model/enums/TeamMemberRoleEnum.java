package com.czl.teamupbackend.model.enums;

import lombok.Getter;

@Getter
public enum TeamMemberRoleEnum {

    CAPTAIN(1, "captain"),
    LEADER(2, "leader"),
    MEMBER(3, "member");

    private final int code;
    private final String desc;

    TeamMemberRoleEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

