package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.model.entity.TeamJoinRequest;
import com.czl.teamupbackend.mapper.TeamJoinRequestMapper;
import com.czl.teamupbackend.service.ITeamJoinRequestService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 加入小组请求表 服务实现类
 * </p>
 *
 * @author czl
 * @since 2026-04-21
 */
@Service
public class TeamJoinRequestServiceImpl extends ServiceImpl<TeamJoinRequestMapper, TeamJoinRequest> implements ITeamJoinRequestService {

}
