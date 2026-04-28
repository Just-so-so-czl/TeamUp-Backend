package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.mapper.TaskListMapper;
import com.czl.teamupbackend.mapper.TaskMapper;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.model.entity.Task;
import com.czl.teamupbackend.model.entity.TaskList;
import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.model.enums.TeamMemberRoleEnum;
import com.czl.teamupbackend.service.ITaskService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 具体任务项表 服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements ITaskService {

    private static final int TASK_STATUS_TODO = 0;
    private static final int TASK_STATUS_DONE = 1;

    private final TaskListMapper taskListMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTask(Long currentUserId, Long taskListId, String description, LocalDateTime deadline) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (taskListId == null || taskListId <= 0) {
            throw new BizException(400, "任务清单ID不合法");
        }
        TaskList taskList = taskListMapper.selectById(taskListId);
        if (taskList == null) {
            throw new BizException(404, "任务清单不存在");
        }
        Team team = teamMapper.selectById(taskList.getTeamId());
        if (team == null) {
            throw new BizException(404, "小组不存在");
        }
        TeamMember selfMember = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, team.getId())
            .eq(TeamMember::getUserId, currentUserId)
            .last("limit 1"));
        if (selfMember == null) {
            throw new BizException(403, "你不是该小组成员");
        }
        TeamMemberRoleEnum role = TeamMemberRoleEnum.fromCode(selfMember.getRole());
        if (role != TeamMemberRoleEnum.CAPTAIN && role != TeamMemberRoleEnum.LEADER) {
            throw new BizException(403, "只有Captain或Leader可以创建任务");
        }

        String validDesc = description == null ? "" : description.trim();
        if (validDesc.isEmpty()) {
            throw new BizException(400, "任务描述不能为空");
        }
        if (validDesc.length() > 500) {
            throw new BizException(400, "任务描述不能超过500个字符");
        }
        if (deadline == null) {
            throw new BizException(400, "任务截止时间不能为空");
        }
        if (taskList.getDeadline() != null && deadline.isAfter(taskList.getDeadline())) {
            throw new BizException(400, "任务截止时间不能晚于任务清单截止时间");
        }

        Task task = new Task();
        task.setTaskListId(taskListId);
        task.setDescription(validDesc);
        task.setStatus(TASK_STATUS_TODO);
        task.setCompletionNote(null);
        task.setDeadline(deadline);
        this.save(task);
        log.info("Task created, teamId={}, taskListId={}, operatorUserId={}, taskId={}",
            team.getId(), taskListId, currentUserId, task.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(Long currentUserId, Long taskId, String completionNote) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (taskId == null || taskId <= 0) {
            throw new BizException(400, "任务ID不合法");
        }
        Task task = this.getById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }
        TaskList taskList = taskListMapper.selectById(task.getTaskListId());
        if (taskList == null) {
            throw new BizException(404, "任务清单不存在");
        }
        Team team = teamMapper.selectById(taskList.getTeamId());
        if (team == null) {
            throw new BizException(404, "小组不存在");
        }
        TeamMember selfMember = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, team.getId())
            .eq(TeamMember::getUserId, currentUserId)
            .last("limit 1"));
        if (selfMember == null) {
            throw new BizException(403, "你不是该小组成员");
        }
        String validNote = completionNote == null ? "" : completionNote.trim();
        if (validNote.length() > 100) {
            throw new BizException(400, "完成备注不能超过100个字符");
        }

        task.setStatus(TASK_STATUS_DONE);
        task.setCompletionNote(validNote.isEmpty() ? null : validNote);
        this.updateById(task);
        log.info("Task completed, teamId={}, taskId={}, userId={}", team.getId(), taskId, currentUserId);
    }
}
