package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.jwt.JwtTokenUtil;
import com.czl.teamupbackend.mapper.UserMapper;
import com.czl.teamupbackend.model.dto.UserLoginRequest;
import com.czl.teamupbackend.model.dto.UserRegisterRequest;
import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.model.vo.LoginResponseVO;
import com.czl.teamupbackend.model.vo.UserSimpleVO;
import com.czl.teamupbackend.service.IUserService;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public void register(UserRegisterRequest request) {
        validateRegisterRequest(request);
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BizException(400, "两次输入的密码不一致");
        }
        boolean emailExists = exists(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail().trim()));
        if (emailExists) {
            throw new BizException(400, "该邮箱已被注册");
        }

        User user = new User()
            .setEmail(request.getEmail().trim())
            .setUsername(request.getUsername().trim())
            .setPassword(passwordEncoder.encode(request.getPassword()))
            .setGender(request.getGender())
            .setAvatar(request.getAvatar());
        save(user);
        log.info("User registered successfully, userId={}, email={}", user.getId(), user.getEmail());
    }

    @Override
    public LoginResponseVO login(UserLoginRequest request) {
        validateLoginRequest(request);

        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail().trim()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(401, "邮箱或密码错误");
        }
        String token = jwtTokenUtil.generateToken(user.getId(), user.getUsername());
        log.info("User login success, userId={}, email={}", user.getId(), user.getEmail());

        return LoginResponseVO.builder()
            .token(token)
            .user(UserSimpleVO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .gender(user.getGender())
                .avatar(user.getAvatar())
                .build())
            .build();
    }

    private void validateRegisterRequest(UserRegisterRequest request) {
        if (request == null) {
            throw new BizException(400, "请求参数不能为空");
        }
        if (isBlank(request.getEmail()) || !EMAIL_PATTERN.matcher(request.getEmail().trim()).matches()) {
            throw new BizException(400, "请输入合法邮箱");
        }
        if (isBlank(request.getUsername()) || request.getUsername().trim().length() < 2 || request.getUsername().trim().length() > 20) {
            throw new BizException(400, "用户名长度需在2到20个字符之间");
        }
        if (isBlank(request.getPassword()) || request.getPassword().length() < 8 || request.getPassword().length() > 32) {
            throw new BizException(400, "密码长度需在8到32位之间");
        }
        if (request.getGender() == null || (request.getGender() != 1 && request.getGender() != 2)) {
            throw new BizException(400, "性别参数不合法");
        }
        if (request.getAvatar() == null || request.getAvatar() < 1 || request.getAvatar() > 8) {
            throw new BizException(400, "头像参数不合法");
        }
        if (request.getGender() == 1 && (request.getAvatar() < 1 || request.getAvatar() > 4)) {
            throw new BizException(400, "男性头像仅支持a1-a4");
        }
        if (request.getGender() == 2 && (request.getAvatar() < 5 || request.getAvatar() > 8)) {
            throw new BizException(400, "女性头像仅支持a5-a8");
        }
    }

    private void validateLoginRequest(UserLoginRequest request) {
        if (request == null) {
            throw new BizException(400, "请求参数不能为空");
        }
        if (isBlank(request.getEmail()) || !EMAIL_PATTERN.matcher(request.getEmail().trim()).matches()) {
            throw new BizException(400, "请输入合法邮箱");
        }
        if (isBlank(request.getPassword())) {
            throw new BizException(400, "密码不能为空");
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
