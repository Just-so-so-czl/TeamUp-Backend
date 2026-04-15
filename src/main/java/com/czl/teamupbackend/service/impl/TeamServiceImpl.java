package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.service.ITeamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 小组/团队信息表 服务实现类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements ITeamService {

}
