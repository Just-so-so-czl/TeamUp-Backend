package com.czl.teamupbackend.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 待处理入组申请项
 */
@Data
@Builder
@Schema(description = "待处理入组申请项")
public class TeamPendingJoinRequestVO {

    @Schema(description = "申请ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    @Schema(description = "申请用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "申请用户名称")
    private String username;

    @Schema(description = "申请用户头像编号")
    private Integer avatar;

    @Schema(description = "申请备注")
    private String description;

    @Schema(description = "申请时间")
    private LocalDateTime createTime;
}

