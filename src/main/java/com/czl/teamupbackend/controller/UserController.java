package com.czl.teamupbackend.controller;


import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.UserLoginRequest;
import com.czl.teamupbackend.model.dto.UserRegisterRequest;
import com.czl.teamupbackend.model.vo.LoginResponseVO;
import com.czl.teamupbackend.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户基础信息表 前端控制器
 * </p>
 *
 * @author czl
 * @since 2026-04-15
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

}
