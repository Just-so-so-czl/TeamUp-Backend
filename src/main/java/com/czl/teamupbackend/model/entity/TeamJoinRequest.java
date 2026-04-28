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
 * 加入小组请求表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("team_join_request")
@Schema(description = "加入小组请求表")
public class TeamJoinRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "雪花ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "申请用户ID")
    private Long userId;

    @Schema(description = "目标小组ID")
    private Long teamId;

    @Schema(description = "申请描述")
    private String description;

    @Schema(description = "状态: 0-待处理, 1-已同意, 2-已拒绝")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
