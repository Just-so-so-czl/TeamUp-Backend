package com.czl.teamupbackend.model.dto;

import lombok.Data;
import java.util.List;

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

    /**
     * 协作文档编辑器中被用户选中的原文。
     */
    private String selectedText;

    /**
     * 选中文本的起始行号，从 1 开始。
     */
    private Integer selectedStartLine;

    /**
     * 选中文本的结束行号，从 1 开始。
     */
    private Integer selectedEndLine;

    /** Documents explicitly selected through the mentor input @ mention picker. */
    private List<Long> mentionedDocumentIds;
}
