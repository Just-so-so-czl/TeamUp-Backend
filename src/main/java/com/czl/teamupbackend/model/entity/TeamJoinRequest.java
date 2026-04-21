package com.czl.teamupbackend.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 加入小组请求表
 * </p>
 *
 * @author czl
 * @since 2026-04-21
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("team_join_request")
@Schema(description="加入小组请求表")
public class TeamJoinRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description="雪花ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description="申请用户ID")
    private Long userId;

    @Schema(description="目标小组ID")
    private Long teamId;

    @Schema(description="状态：0-待处理，1-已同意，2-已拒绝")
    private Integer status;

    @Schema(description="创建时间")
    private LocalDateTime createTime;

    @Schema(description="更新时间")
    private LocalDateTime updateTime;


}
