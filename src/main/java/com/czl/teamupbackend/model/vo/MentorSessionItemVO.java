package com.czl.teamupbackend.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorSessionItemVO {

    private String sessionId;

    private String title;

    private String status;

    private Integer messageCount;

    private LocalDateTime lastMessageAt;
}

