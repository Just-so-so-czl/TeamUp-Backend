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
 * 用户基础信息表
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")
@Schema(description="用户基础信息表")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "雪花算法生成的分布式唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户邮箱")
    private String email;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "加密后的密码")
    private String password;

    @Schema(description = "头像枚举: 1-8 代表不同的预设头像")
    private Integer avatar;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;


}
