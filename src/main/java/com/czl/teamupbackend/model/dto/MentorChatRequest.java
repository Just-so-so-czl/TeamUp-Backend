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

    /**
     * 会话类型：TEAM_MENTOR / COLLAB_DOC。
     */
    private String sessionType;

    /**
     * 协作文档ID，sessionType=COLLAB_DOC 时使用。
     */
    private Long documentId;
}
