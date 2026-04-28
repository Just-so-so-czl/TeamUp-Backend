package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小组详情查询请求
 */
@Data
@Schema(description = "小组详情查询请求")
public class TeamDetailRequest {

    @Schema(description = "小组ID")
    private Long teamId;
}
