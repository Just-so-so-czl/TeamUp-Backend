package com.czl.teamupbackend.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorChatMessageItemVO {

    private String messageId;

    private String senderType;

    private String messageType;

    private String content;

    private LocalDateTime createdAt;
}

