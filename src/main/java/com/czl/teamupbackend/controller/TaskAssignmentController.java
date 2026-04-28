package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.TaskAssignRequest;
import com.czl.teamupbackend.model.dto.TaskAssigneeRemoveRequest;
import com.czl.teamupbackend.model.dto.TaskClaimRequest;
import com.czl.teamupbackend.service.ITaskAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务负责人控制器
 */
@RestController
@RequestMapping("/task-assignment")
@Slf4j
@RequiredArgsConstructor
public class TaskAssignmentController {

    private final ITaskAssignmentService taskAssignmentService;

    @PostMapping("/claim")
    public Result<Void> claim(@RequestBody TaskClaimRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTaskId() == null) {
            throw new BizException(400, "任务ID不能为空");
        }
        taskAssignmentService.claimTask(userId, request.getTaskId());
        return Result.success("任务认领成功", null);
    }

    @PostMapping("/assign")
    public Result<Void> assign(@RequestBody TaskAssignRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTaskId() == null || request.getAssigneeUserId() == null) {
            throw new BizException(400, "参数不完整");
        }
        taskAssignmentService.assignTask(userId, request.getTaskId(), request.getAssigneeUserId());
        return Result.success("负责人添加成功", null);
    }

    @PostMapping("/remove-assignee")
    public Result<Void> removeAssignee(@RequestBody TaskAssigneeRemoveRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getTaskId() == null || request.getAssigneeUserId() == null) {
            throw new BizException(400, "参数不完整");
        }
        taskAssignmentService.removeTaskAssignee(userId, request.getTaskId(), request.getAssigneeUserId());
        return Result.success("负责人移除成功", null);
    }
}

