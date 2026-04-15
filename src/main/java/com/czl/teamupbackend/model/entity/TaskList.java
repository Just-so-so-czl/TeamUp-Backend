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
 * 小组项目任务清单表
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("task_list")
@Schema(description="小组项目任务清单表")
public class TaskList implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "雪花算法生成的分布式唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属小组ID")
    private Long teamId;

    @Schema(description = "任务清单标题")
    private String title;

    @Schema(description = "任务清单详细描述")
    private String description;

    @Schema(description = "创建者用户ID")
    private Long creatorId;

    @Schema(description = "清单最终截止日期")
    private LocalDateTime deadline;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;


}
