package com.czl.teamupbackend.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 小组详情响应
 */
@Data
@Builder
@Schema(description = "小组详情响应")
public class TeamDetailVO {

    @Schema(description = "小组ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;

    @Schema(description = "组名")
    private String teamName;

    @Schema(description = "小组描述")
    private String description;

    @Schema(description = "邀请码")
    private String inviteCode;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "当前用户是否为组长")
    private Boolean currentUserCaptain;
}
