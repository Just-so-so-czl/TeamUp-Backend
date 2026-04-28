package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.entity.TaskList;
import com.czl.teamupbackend.model.vo.TeamTaskListVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 小组项目任务清单表 服务类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
public interface ITaskListService extends IService<TaskList> {

    /**
     * 查询小组下的任务清单与清单任务项
     *
     * @param currentUserId 当前用户ID
     * @param teamId 小组ID
     * @return 任务清单数据
     */
    TeamTaskListVO listTeamTaskLists(Long currentUserId, Long teamId);

    /**
     * 创建任务清单（仅Captain/Leader）
     *
     * @param currentUserId 当前用户ID
     * @param teamId 小组ID
     * @param title 清单标题
     * @param description 清单描述
     * @param deadline 清单截止时间
     */
    void createTaskList(Long currentUserId, Long teamId, String title, String description, java.time.LocalDateTime deadline);

    /**
     * 修改任务清单（仅Captain/Leader）
     *
     * @param currentUserId 当前用户ID
     * @param taskListId 任务清单ID
     * @param title 清单标题
     * @param description 清单描述
     * @param deadline 清单截止时间
     */
    void updateTaskList(Long currentUserId, Long taskListId, String title, String description, java.time.LocalDateTime deadline);

    /**
     * 删除任务清单（仅Captain/Leader）
     *
     * @param currentUserId 当前用户ID
     * @param taskListId 任务清单ID
     */
    void deleteTaskList(Long currentUserId, Long taskListId);
}
