package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.service.ITeamMemberService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 小组库成员关联表 服务实现类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Service
public class TeamMemberServiceImpl extends ServiceImpl<TeamMemberMapper, TeamMember> implements ITeamMemberService {

}
