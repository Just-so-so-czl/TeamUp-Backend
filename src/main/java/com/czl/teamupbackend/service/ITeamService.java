package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.model.dto.TeamCreateRequest;
import com.czl.teamupbackend.model.dto.TeamDetailRequest;
import com.czl.teamupbackend.model.dto.TeamUpdateRequest;
import com.czl.teamupbackend.model.vo.TeamCreateResponseVO;
import com.czl.teamupbackend.model.vo.TeamDetailVO;
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

    TeamCreateResponseVO createTeam(Long userId, TeamCreateRequest request);

    TeamDetailVO getTeamDetail(Long userId, TeamDetailRequest request);

    void updateTeamInfo(Long userId, TeamUpdateRequest request);
}
