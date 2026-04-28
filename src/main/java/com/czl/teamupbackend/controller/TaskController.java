package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.TaskCompleteRequest;
import com.czl.teamupbackend.model.dto.TeamTaskCreateRequest;
import com.czl.teamupbackend.service.ITaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务控制器
 */
@RestController
@RequestMapping("/task")
@Slf4j
@RequiredArgsConstructor
public class TaskController {

    private final ITaskService taskService;

    @PostMapping("/create")
    public Result<Void> create(@RequestBody TeamTaskCreateRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTaskListId() == null) {
            throw new BizException(400, "任务清单ID不能为空");
        }
        taskService.createTask(userId, request.getTaskListId(), request.getDescription(), request.getDeadline());
        log.info("Task create endpoint handled, userId={}, taskListId={}", userId, request.getTaskListId());
        return Result.success("任务创建成功", null);
    }

    @PostMapping("/complete")
    public Result<Void> complete(@RequestBody TaskCompleteRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTaskId() == null) {
            throw new BizException(400, "任务ID不能为空");
        }
        taskService.completeTask(userId, request.getTaskId(), request.getCompletionNote());
        return Result.success("任务已完成", null);
    }
}

