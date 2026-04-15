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
 * 小组库成员关联表
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("team_member")
@Schema(description="小组库成员关联表")
public class TeamMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "小组ID")
    private Long teamId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "角色枚举: 1-组长, 2-管理员, 3-普通组员")
    private Integer role;

    @Schema(description = "加入小组时间")
    private LocalDateTime joinTime;


}
