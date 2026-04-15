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
 * 小组/团队信息表
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("team")
@Schema(description="小组/团队信息表")
public class Team implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "雪花算法生成的分布式唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "小组名称")
    private String name;

    @Schema(description = "创建者ID(组长)")
    private Long ownerId;

    @Schema(description = "小组邀请码")
    private String inviteCode;

    @Schema(description = "小组简介")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;


}
