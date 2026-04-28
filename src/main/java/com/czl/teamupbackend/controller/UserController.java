package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.UserLoginRequest;
import com.czl.teamupbackend.model.dto.UserRegisterRequest;
import com.czl.teamupbackend.model.vo.LoginResponseVO;
import com.czl.teamupbackend.model.vo.UserSimpleVO;
import com.czl.teamupbackend.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @PostMapping("/register")
    public Result<Void> register(@RequestBody UserRegisterRequest request) {
        userService.register(request);
        log.info("Register endpoint handled for email={}", request.getEmail());
        return Result.success("注册成功", null);
    }

    @PostMapping("/login")
    public Result<LoginResponseVO> login(@RequestBody UserLoginRequest request) {
        LoginResponseVO response = userService.login(request);
        return Result.success("登录成功", response);
    }

    @PostMapping("/me")
    public Result<UserSimpleVO> me() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        UserSimpleVO user = userService.getCurrentUserInfo(userId);
        return Result.success("查询成功", user);
    }
}
