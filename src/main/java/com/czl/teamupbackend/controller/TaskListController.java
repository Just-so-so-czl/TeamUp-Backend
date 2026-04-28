package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.TeamTaskListCreateRequest;
import com.czl.teamupbackend.model.dto.TeamTaskListDeleteRequest;
import com.czl.teamupbackend.model.dto.TeamTaskListQueryRequest;
import com.czl.teamupbackend.model.dto.TeamTaskListUpdateRequest;
import com.czl.teamupbackend.model.vo.TeamTaskListVO;
import com.czl.teamupbackend.service.ITaskListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小组任务清单控制器
 */
@RestController
@RequestMapping("/task-list")
@Slf4j
@RequiredArgsConstructor
public class TaskListController {

    private final ITaskListService taskListService;

    @PostMapping("/team-list")
    public Result<TeamTaskListVO> teamList(@RequestBody TeamTaskListQueryRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null) {
            throw new BizException(400, "小组ID不能为空");
        }
        TeamTaskListVO response = taskListService.listTeamTaskLists(userId, request.getTeamId());
        return Result.success("查询成功", response);
    }

    @PostMapping("/create")
    public Result<Void> create(@RequestBody TeamTaskListCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTeamId() == null) {
            throw new BizException(400, "小组ID不能为空");
        }
        taskListService.createTaskList(
            userId,
            request.getTeamId(),
            request.getTitle(),
            request.getDescription(),
            request.getDeadline()
        );
        log.info("Task list create endpoint handled, userId={}, teamId={}", userId, request.getTeamId());
        return Result.success("任务清单创建成功", null);
    }

    @PostMapping("/update")
    public Result<Void> update(@RequestBody TeamTaskListUpdateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTaskListId() == null) {
            throw new BizException(400, "任务清单ID不能为空");
        }
        taskListService.updateTaskList(
            userId,
            request.getTaskListId(),
            request.getTitle(),
            request.getDescription(),
            request.getDeadline()
        );
        log.info("Task list update endpoint handled, userId={}, taskListId={}", userId, request.getTaskListId());
        return Result.success("任务清单修改成功", null);
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody TeamTaskListDeleteRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTaskListId() == null) {
            throw new BizException(400, "任务清单ID不能为空");
        }
        taskListService.deleteTaskList(userId, request.getTaskListId());
        log.info("Task list delete endpoint handled, userId={}, taskListId={}", userId, request.getTaskListId());
        return Result.success("任务清单删除成功", null);
    }
}

