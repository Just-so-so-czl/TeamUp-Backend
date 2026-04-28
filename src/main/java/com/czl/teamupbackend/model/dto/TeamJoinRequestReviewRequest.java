package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 入组申请审核请求
 */
@Data
@Schema(description = "入组申请审核请求")
public class TeamJoinRequestReviewRequest {

    @Schema(description = "申请ID")
    private Long requestId;
}

