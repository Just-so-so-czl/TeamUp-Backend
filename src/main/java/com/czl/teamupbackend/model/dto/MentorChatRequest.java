package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class MentorChatRequest {

    private String message;

    private Long teamId;

    /**
     * 会话ID，前端首次可不传，后端自动创建并回传。
     */
    private Long sessionId;
}
