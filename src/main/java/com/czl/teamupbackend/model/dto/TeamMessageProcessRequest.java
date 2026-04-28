package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 处理消息请求
 */
@Data
@Schema(description = "处理消息请求")
public class TeamMessageProcessRequest {

    @Schema(description = "消息ID")
    private Long messageId;
}
