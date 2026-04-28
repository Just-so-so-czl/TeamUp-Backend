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
 * 具体任务项表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("task")
@Schema(description = "具体任务项表")
public class Task implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "雪花算法生成的分布式唯一ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "所属任务清单ID")
    private Long taskListId;

    @Schema(description = "任务具体描述")
    private String description;

    @Schema(description = "任务状态: 0-待办, 1-完成")
    private Integer status;

    @Schema(description = "任务完成备注")
    private String completionNote;

    @Schema(description = "任务截止时间")
    private LocalDateTime deadline;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

