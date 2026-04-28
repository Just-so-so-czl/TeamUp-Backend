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
import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.model.enums.TeamMemberRoleEnum;
import com.czl.teamupbackend.model.vo.TeamTaskItemVO;
import com.czl.teamupbackend.model.vo.TeamTaskAssigneeVO;
import com.czl.teamupbackend.model.vo.TeamTaskListItemVO;
import com.czl.teamupbackend.model.vo.TeamTaskListVO;
import com.czl.teamupbackend.service.ITaskListService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 小组项目任务清单表 服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskListServiceImpl extends ServiceImpl<TaskListMapper, TaskList> implements ITaskListService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TaskMapper taskMapper;
    private final TaskAssignmentMapper taskAssignmentMapper;
    private final UserMapper userMapper;

    @Override
    public TeamTaskListVO listTeamTaskLists(Long currentUserId, Long teamId) {
        TeamMember selfMember = validateMembership(currentUserId, teamId);
        List<TaskList> taskLists = this.list(new LambdaQueryWrapper<TaskList>()
            .eq(TaskList::getTeamId, teamId)
            .orderByAsc(TaskList::getDeadline)
            .orderByDesc(TaskList::getCreateTime));

        if (taskLists.isEmpty()) {
            return TeamTaskListVO.builder()
                .currentUserCanCreate(canCreateTaskList(selfMember))
                .taskLists(new ArrayList<>())
                .build();
        }

        List<Long> taskListIds = taskLists.stream().map(TaskList::getId).collect(Collectors.toList());
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
            .in(Task::getTaskListId, taskListIds)
            .orderByAsc(Task::getStatus)
            .orderByAsc(Task::getDeadline)
            .orderByDesc(Task::getCreateTime));
        Map<Long, List<Task>> taskMap = tasks.stream().collect(Collectors.groupingBy(Task::getTaskListId));
        List<Long> taskIds = tasks.stream().map(Task::getId).distinct().collect(Collectors.toList());
        Map<Long, List<TaskAssignment>> assignmentMap;
        if (taskIds.isEmpty()) {
            assignmentMap = Collections.emptyMap();
        } else {
            assignmentMap = taskAssignmentMapper.selectList(new LambdaQueryWrapper<TaskAssignment>()
                    .in(TaskAssignment::getTaskId, taskIds))
                .stream()
                .collect(Collectors.groupingBy(TaskAssignment::getTaskId));
        }

        List<Long> creatorIds = taskLists.stream().map(TaskList::getCreatorId).distinct().collect(Collectors.toList());
        List<Long> assigneeIds = assignmentMap.values().stream()
            .flatMap(List::stream)
            .map(TaskAssignment::getUserId)
            .distinct()
            .collect(Collectors.toList());
        List<Long> allUserIds = new ArrayList<>();
        allUserIds.addAll(creatorIds);
        allUserIds.addAll(assigneeIds);
        allUserIds = allUserIds.stream().distinct().collect(Collectors.toList());
        Map<Long, String> creatorNameMap;
        if (allUserIds.isEmpty()) {
            creatorNameMap = Collections.emptyMap();
        } else {
            creatorNameMap = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, allUserIds))
                .stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        }

        List<TeamTaskListItemVO> listItems = taskLists.stream().map(item -> {
            List<TeamTaskItemVO> taskItems = taskMap.getOrDefault(item.getId(), new ArrayList<>())
                .stream()
                .map(task -> {
                    List<TeamTaskAssigneeVO> assignees = assignmentMap.getOrDefault(task.getId(), new ArrayList<>())
                        .stream()
                        .map(assignment -> TeamTaskAssigneeVO.builder()
                            .userId(assignment.getUserId())
                            .username(creatorNameMap.getOrDefault(assignment.getUserId(), "未知成员"))
                            .build())
                        .collect(Collectors.toList());
                    return TeamTaskItemVO.builder()
                        .taskId(task.getId())
                        .description(task.getDescription())
                        .status(task.getStatus())
                        .deadline(task.getDeadline())
                        .completionNote(task.getStatus() != null && task.getStatus() == 1 ? task.getCompletionNote() : null)
                        .assignees(assignees)
                        .build();
                })
                .collect(Collectors.toList());
            return TeamTaskListItemVO.builder()
                .taskListId(item.getId())
                .title(item.getTitle())
                .description(item.getDescription())
                .creatorId(item.getCreatorId())
                .creatorName(creatorNameMap.getOrDefault(item.getCreatorId(), "未知用户"))
                .deadline(item.getDeadline())
                .tasks(taskItems)
                .build();
        }).collect(Collectors.toList());

        return TeamTaskListVO.builder()
            .currentUserCanCreate(canCreateTaskList(selfMember))
            .taskLists(listItems)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTaskList(Long currentUserId, Long teamId, String title, String description, LocalDateTime deadline) {
        TeamMember selfMember = validateMembership(currentUserId, teamId);
        if (!canCreateTaskList(selfMember)) {
            throw new BizException(403, "只有Captain或Leader可以创建任务清单");
        }
        String validTitle = title == null ? "" : title.trim();
        String validDesc = description == null ? "" : description.trim();
        if (validTitle.length() < 2 || validTitle.length() > 150) {
            throw new BizException(400, "清单标题长度需在2到150个字符之间");
        }
        if (validDesc.length() > 1000) {
            throw new BizException(400, "清单描述不能超过1000个字符");
        }
        if (deadline == null) {
            throw new BizException(400, "清单截止时间不能为空");
        }

        TaskList taskList = new TaskList();
        taskList.setTeamId(teamId);
        taskList.setTitle(validTitle);
        taskList.setDescription(validDesc.isEmpty() ? null : validDesc);
        taskList.setCreatorId(currentUserId);
        taskList.setDeadline(deadline);
        this.save(taskList);
        log.info("Task list created, teamId={}, creatorId={}, taskListId={}", teamId, currentUserId, taskList.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskList(Long currentUserId, Long taskListId, String title, String description, LocalDateTime deadline) {
        if (taskListId == null || taskListId <= 0) {
            throw new BizException(400, "任务清单ID不合法");
        }
        TaskList taskList = this.getById(taskListId);
        if (taskList == null) {
            throw new BizException(404, "任务清单不存在");
        }
        TeamMember selfMember = validateMembership(currentUserId, taskList.getTeamId());
        if (!canCreateTaskList(selfMember)) {
            throw new BizException(403, "只有Captain或Leader可以修改任务清单");
        }

        String validTitle = title == null ? "" : title.trim();
        String validDesc = description == null ? "" : description.trim();
        if (validTitle.length() < 2 || validTitle.length() > 150) {
            throw new BizException(400, "清单标题长度需在2到150个字符之间");
        }
        if (validDesc.length() > 1000) {
            throw new BizException(400, "清单描述不能超过1000个字符");
        }
        if (deadline == null) {
            throw new BizException(400, "清单截止时间不能为空");
        }

        taskList.setTitle(validTitle);
        taskList.setDescription(validDesc.isEmpty() ? null : validDesc);
        taskList.setDeadline(deadline);
        this.updateById(taskList);
        log.info("Task list updated, taskListId={}, operatorUserId={}", taskListId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTaskList(Long currentUserId, Long taskListId) {
        if (taskListId == null || taskListId <= 0) {
            throw new BizException(400, "任务清单ID不合法");
        }
        TaskList taskList = this.getById(taskListId);
        if (taskList == null) {
            throw new BizException(404, "任务清单不存在");
        }
        TeamMember selfMember = validateMembership(currentUserId, taskList.getTeamId());
        if (!canCreateTaskList(selfMember)) {
            throw new BizException(403, "只有Captain或Leader可以删除任务清单");
        }

        List<Task> taskItems = taskMapper.selectList(new LambdaQueryWrapper<Task>()
            .eq(Task::getTaskListId, taskListId));
        if (!taskItems.isEmpty()) {
            List<Long> taskIds = taskItems.stream().map(Task::getId).collect(Collectors.toList());
            taskAssignmentMapper.delete(new LambdaQueryWrapper<TaskAssignment>()
                .in(TaskAssignment::getTaskId, taskIds));
            taskMapper.delete(new LambdaQueryWrapper<Task>()
                .eq(Task::getTaskListId, taskListId));
        }
        this.removeById(taskListId);
        log.info("Task list deleted, taskListId={}, operatorUserId={}", taskListId, currentUserId);
    }

    private TeamMember validateMembership(Long currentUserId, Long teamId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (teamId == null || teamId <= 0) {
            throw new BizException(400, "小组ID不合法");
        }
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BizException(404, "小组不存在");
        }
        TeamMember selfMember = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, teamId)
            .eq(TeamMember::getUserId, currentUserId)
            .last("limit 1"));
        if (selfMember == null) {
            throw new BizException(403, "你不是该小组成员");
        }
        return selfMember;
    }

    private boolean canCreateTaskList(TeamMember teamMember) {
        if (teamMember == null) {
            return false;
        }
        TeamMemberRoleEnum roleEnum = TeamMemberRoleEnum.fromCode(teamMember.getRole());
        return roleEnum == TeamMemberRoleEnum.CAPTAIN || roleEnum == TeamMemberRoleEnum.LEADER;
    }
}
