package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.model.dto.TeamCreateRequest;
import com.czl.teamupbackend.model.vo.TeamCreateResponseVO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 小组/团队信息表 服务类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
public interface ITeamService extends IService<Team> {

    TeamCreateResponseVO createTeam(TeamCreateRequest request);
}
