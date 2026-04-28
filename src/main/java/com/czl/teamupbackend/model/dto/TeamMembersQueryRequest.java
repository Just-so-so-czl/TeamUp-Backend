package com.czl.teamupbackend.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小组成员查询请求
 */
@Data
@Schema(description = "小组成员查询请求")
public class TeamMembersQueryRequest {

    @Schema(description = "小组ID")
    private Long teamId;
}

