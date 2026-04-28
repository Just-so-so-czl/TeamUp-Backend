package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.model.dto.UserLoginRequest;
import com.czl.teamupbackend.model.dto.UserRegisterRequest;
import com.czl.teamupbackend.model.vo.LoginResponseVO;
import com.czl.teamupbackend.model.vo.UserSimpleVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 用户基础信息表 服务类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
public interface IUserService extends IService<User> {

    void register(UserRegisterRequest request);

    LoginResponseVO login(UserLoginRequest request);

    UserSimpleVO getCurrentUserInfo(Long userId);
}
