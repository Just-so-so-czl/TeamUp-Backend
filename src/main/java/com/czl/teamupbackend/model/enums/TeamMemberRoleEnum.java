package com.czl.teamupbackend.model.enums;

import com.czl.teamupbackend.commen.exception.BizException;
import lombok.Getter;

@Getter
public enum TeamMemberRoleEnum {

    CAPTAIN(1, "Captain", "组长，负责审批与统筹"),
    LEADER(2, "Leader", "模块负责人"),
    MEMBER(3, "Member", "普通成员");

    private final int code;
    private final String roleName;
    private final String roleDesc;

    TeamMemberRoleEnum(int code, String roleName, String roleDesc) {
        this.code = code;
        this.roleName = roleName;
        this.roleDesc = roleDesc;
    }

    public static TeamMemberRoleEnum fromCode(Integer code) {
        if (code == null) {
            return MEMBER;
        }
        for (TeamMemberRoleEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new BizException(400, "成员角色不合法");
    }
}

