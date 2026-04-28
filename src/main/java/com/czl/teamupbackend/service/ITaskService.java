package com.czl.teamupbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.czl.teamupbackend.model.entity.Task;
import java.time.LocalDateTime;

/**
 * 任务服务
 */
public interface ITaskService extends IService<Task> {

    /**
     * 创建任务（仅Captain/Leader）
     *
     * @param currentUserId 当前用户ID
     * @param taskListId 任务清单ID
     * @param description 任务描述
     * @param deadline 任务截止时间
     */
    void createTask(Long currentUserId, Long taskListId, String description, LocalDateTime deadline);

    /**
     * 完成任务（可选填写完成备注）
     *
     * @param currentUserId 当前用户ID
     * @param taskId 任务ID
     * @param completionNote 完成备注
     */
    void completeTask(Long currentUserId, Long taskId, String completionNote);
}

