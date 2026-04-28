package com.czl.teamupbackend.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 我的小组信息
 */
@Data
@Builder
@Schema(description = "我的小组信息")
public class MyTeamVO {

    @Schema(description = "小组ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;

    @Schema(description = "组名")
    private String teamName;

    @Schema(description = "小组描述")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "成员总数")
    private Integer memberCount;

    @Schema(description = "当前用户角色编码: 1-Captain, 2-Leader, 3-Member")
    private Integer userRoleCode;

    @Schema(description = "当前用户角色名称（首字母大写）")
    private String userRoleName;

    @Schema(description = "小组成员列表")
    private List<TeamMemberBriefVO> members;
}

