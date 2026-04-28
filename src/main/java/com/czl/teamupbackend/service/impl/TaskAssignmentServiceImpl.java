package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.mapper.TaskAssignmentMapper;
import com.czl.teamupbackend.mapper.TaskListMapper;
import com.czl.teamupbackend.mapper.TaskMapper;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.mapper.UserMapper;
import com.czl.teamupbackend.model.entity.Task;
import com.czl.teamupbackend.model.entity.TaskAssignment;
import com.czl.teamupbackend.model.entity.TaskList;
import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.model.entity.TeamMessage;
import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.model.enums.TeamMemberRoleEnum;
import com.czl.teamupbackend.model.vo.WebSocketNotifyVO;
import com.czl.teamupbackend.realtime.OnlineUserSessionManager;
import com.czl.teamupbackend.service.ITaskAssignmentService;
import com.czl.teamupbackend.service.ITeamMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务负责人分配表 服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskAssignmentServiceImpl extends ServiceImpl<TaskAssignmentMapper, TaskAssignment> implements ITaskAssignmentService {

    private static final int MESSAGE_TYPE_TASK_CLAIM = 2;
    private static final int MESSAGE_TYPE_TASK_ASSIGN = 3;
    private static final int MESSAGE_UNPROCESSED = 0;

    private final TaskMapper taskMapper;
    private final TaskListMapper taskListMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ITeamMessageService teamMessageService;
    private final OnlineUserSessionManager onlineUserSessionManager;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimTask(Long currentUserId, Long taskId) {
        TaskContext taskContext = validateAndBuildTaskContext(currentUserId, taskId);
        ensureAssigneeMembership(taskContext.team.getId(), currentUserId);
        boolean created = createAssignmentIfAbsent(taskId, currentUserId);
        if (created) {
            pushClaimMessage(taskContext, currentUserId);
        }
        log.info("Task claimed, taskId={}, userId={}", taskId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTask(Long operatorUserId, Long taskId, Long assigneeUserId) {
        TaskContext taskContext = validateAndBuildTaskContext(operatorUserId, taskId);
        TeamMember operatorMember = ensureAssigneeMembership(taskContext.team.getId(), operatorUserId);
        TeamMemberRoleEnum roleEnum = TeamMemberRoleEnum.fromCode(operatorMember.getRole());
        if (roleEnum != TeamMemberRoleEnum.CAPTAIN && roleEnum != TeamMemberRoleEnum.LEADER) {
            throw new BizException(403, "只有Captain或Leader可以分配任务");
        }
        ensureAssigneeMembership(taskContext.team.getId(), assigneeUserId);
        boolean created = createAssignmentIfAbsent(taskId, assigneeUserId);
        if (created) {
            pushAssignMessage(taskContext, operatorUserId, assigneeUserId);
        }
        log.info("Task assigned, taskId={}, operatorUserId={}, assigneeUserId={}", taskId, operatorUserId, assigneeUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTaskAssignee(Long operatorUserId, Long taskId, Long assigneeUserId) {
        TaskContext taskContext = validateAndBuildTaskContext(operatorUserId, taskId);
        TeamMember operatorMember = ensureAssigneeMembership(taskContext.team.getId(), operatorUserId);
        TeamMemberRoleEnum roleEnum = TeamMemberRoleEnum.fromCode(operatorMember.getRole());
        if (roleEnum != TeamMemberRoleEnum.CAPTAIN && roleEnum != TeamMemberRoleEnum.LEADER) {
            throw new BizException(403, "只有Captain或Leader可以移除负责人");
        }
        boolean removed = this.remove(new LambdaQueryWrapper<TaskAssignment>()
            .eq(TaskAssignment::getTaskId, taskId)
            .eq(TaskAssignment::getUserId, assigneeUserId));
        if (!removed) {
            throw new BizException(404, "该成员不是当前任务负责人");
        }
        log.info("Task assignee removed, taskId={}, operatorUserId={}, assigneeUserId={}", taskId, operatorUserId, assigneeUserId);
    }

    private TaskContext validateAndBuildTaskContext(Long userId, Long taskId) {
        if (userId == null || userId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (taskId == null || taskId <= 0) {
            throw new BizException(400, "任务ID不合法");
        }
        Task task = taskMapper.selectById(taskId);
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
        ensureAssigneeMembership(team.getId(), userId);
        return new TaskContext(task, taskList, team);
    }

    private TeamMember ensureAssigneeMembership(Long teamId, Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(400, "成员用户ID不合法");
        }
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, teamId)
            .eq(TeamMember::getUserId, userId)
            .last("limit 1"));
        if (member == null) {
            throw new BizException(403, "目标用户不是该小组成员");
        }
        return member;
    }

    private boolean createAssignmentIfAbsent(Long taskId, Long userId) {
        boolean exists = this.count(new LambdaQueryWrapper<TaskAssignment>()
            .eq(TaskAssignment::getTaskId, taskId)
            .eq(TaskAssignment::getUserId, userId)) > 0;
        if (exists) {
            return false;
        }
        TaskAssignment assignment = new TaskAssignment();
        assignment.setTaskId(taskId);
        assignment.setUserId(userId);
        assignment.setAssignTime(LocalDateTime.now());
        this.save(assignment);
        return true;
    }

    private void pushClaimMessage(TaskContext context, Long claimerUserId) {
        if (context.taskList.getCreatorId() == null || context.taskList.getCreatorId().equals(claimerUserId)) {
            return;
        }
        User claimer = userMapper.selectById(claimerUserId);
        String claimerName = claimer == null ? "某位同学" : claimer.getUsername();
        TeamMessage message = new TeamMessage()
            .setTitle("任务被认领")
            .setContent("成员【" + claimerName + "】认领了任务：" + context.task.getDescription())
            .setTeamId(context.team.getId())
            .setType(MESSAGE_TYPE_TASK_CLAIM)
            .setUserId(context.taskList.getCreatorId())
            .setRelatedUrl(buildTaskRelatedUrl(context.team.getId()))
            .setIsProcessed(MESSAGE_UNPROCESSED);
        teamMessageService.save(message);
        pushWebSocketNotify(context.taskList.getCreatorId(), context.team.getId(), "任务被认领", message.getContent());
    }

    private void pushAssignMessage(TaskContext context, Long operatorUserId, Long assigneeUserId) {
        if (assigneeUserId.equals(operatorUserId)) {
            return;
        }
        User operator = userMapper.selectById(operatorUserId);
        String operatorName = operator == null ? "小组管理员" : operator.getUsername();
        TeamMessage message = new TeamMessage()
            .setTitle("任务已分配给你")
            .setContent("【" + operatorName + "】将任务分配给你：" + context.task.getDescription())
            .setTeamId(context.team.getId())
            .setType(MESSAGE_TYPE_TASK_ASSIGN)
            .setUserId(assigneeUserId)
            .setRelatedUrl(buildTaskRelatedUrl(context.team.getId()))
            .setIsProcessed(MESSAGE_UNPROCESSED);
        teamMessageService.save(message);
        pushWebSocketNotify(assigneeUserId, context.team.getId(), "你有新的任务分配", message.getContent());
    }

    private void pushWebSocketNotify(Long targetUserId, Long teamId, String title, String content) {
        if (targetUserId == null || !onlineUserSessionManager.isOnline(targetUserId)) {
            return;
        }
        WebSocketNotifyVO notifyVO = WebSocketNotifyVO.builder()
            .type("NEW_TEAM_MESSAGE")
            .title(title)
            .content(content)
            .teamId(teamId)
            .build();
        try {
            String payload = objectMapper.writeValueAsString(notifyVO);
            onlineUserSessionManager.sendToUser(targetUserId, payload);
        } catch (Exception e) {
            log.warn("Build websocket notify payload failed, userId={}, error={}", targetUserId, e.getMessage());
        }
    }

    private String buildTaskRelatedUrl(Long teamId) {
        if (teamId == null) {
            return "/messages";
        }
        return "/teams/" + teamId + "?tab=tasks";
    }

    private record TaskContext(Task task, TaskList taskList, Team team) {
    }
}
