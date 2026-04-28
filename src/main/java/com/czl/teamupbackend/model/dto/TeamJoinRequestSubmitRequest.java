package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 提交加入小组申请请求
 */
@Data
@Schema(description = "提交加入小组申请请求")
public class TeamJoinRequestSubmitRequest {

    @Schema(description = "小组邀请码")
    private String inviteCode;

    @Schema(description = "申请备注")
    private String description;
}
