package com.czl.teamupbackend.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * WebSocket 实时通知消息
 */
@Data
@Builder
public class WebSocketNotifyVO {

    private String type;

    private String title;

    private String content;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;
}

