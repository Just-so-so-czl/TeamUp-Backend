package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.mapper.UserMapper;
import com.czl.teamupbackend.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户基础信息表 服务实现类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

}
