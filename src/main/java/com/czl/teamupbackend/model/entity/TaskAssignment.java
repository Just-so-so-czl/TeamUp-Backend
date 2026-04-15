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
 * 任务负责人分配表
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("task_assignment")
@Schema(description="任务负责人分配表")
public class TaskAssignment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "雪花算法生成的分布式唯一ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联的任务项ID")
    private Long taskId;

    @Schema(description = "认领人用户ID")
    private Long userId;

    @Schema(description = "认领/分配时间")
    private LocalDateTime assignTime;


}
