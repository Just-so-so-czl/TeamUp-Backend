package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.vo.TeamMessageListVO;
import com.czl.teamupbackend.service.ITeamMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息控制器
 */
@RestController
@RequestMapping("/team-message")
@RequiredArgsConstructor
public class TeamMessageController {

    private final ITeamMessageService teamMessageService;

    @PostMapping("/my-list")
    public Result<TeamMessageListVO> myList(@RequestBody(required = false) Object ignored) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        TeamMessageListVO response = teamMessageService.listMyMessages(userId);
        return Result.success("查询成功", response);
    }

    @PostMapping("/unread-count")
    public Result<Long> unreadCount(@RequestBody(required = false) Object ignored) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        long unreadCount = teamMessageService.countUnreadMessages(userId);
        return Result.success("查询成功", unreadCount);
    }
}
