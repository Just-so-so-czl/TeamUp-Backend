package com.czl.teamupbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 小组成员关联表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("team_member")
@Schema(description = "小组成员关联表")
public class TeamMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "唯一ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "小组ID")
    private Long teamId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "成员身份：1-captain(组长), 2-leader(模块负责人), 3-member(普通组员)")
    private Integer role;

    @Schema(description = "加入小组时间")
    private LocalDateTime joinTime;
}