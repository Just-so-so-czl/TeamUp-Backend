package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.entity.TaskAssignment;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 任务负责人分配表 服务类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
public interface ITaskAssignmentService extends IService<TaskAssignment> {

    /**
     * 成员认领任务
     *
     * @param currentUserId 当前用户ID
     * @param taskId 任务ID
     */
    void claimTask(Long currentUserId, Long taskId);

    /**
     * Captain/Leader 分配任务负责人
     *
     * @param operatorUserId 操作人ID
     * @param taskId 任务ID
     * @param assigneeUserId 被分配成员ID
     */
    void assignTask(Long operatorUserId, Long taskId, Long assigneeUserId);

    /**
     * Captain/Leader 移除任务负责人
     *
     * @param operatorUserId 操作人ID
     * @param taskId 任务ID
     * @param assigneeUserId 被移除成员ID
     */
    void removeTaskAssignee(Long operatorUserId, Long taskId, Long assigneeUserId);
}
