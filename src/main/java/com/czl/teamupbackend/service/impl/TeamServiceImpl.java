package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.mapper.UserMapper;
import com.czl.teamupbackend.model.dto.TeamCreateRequest;
import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.model.enums.TeamMemberRoleEnum;
import com.czl.teamupbackend.model.vo.TeamCreateResponseVO;
import com.czl.teamupbackend.service.ITeamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 小组/团队信息表 服务实现类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements ITeamService {

    private static final String INVITE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int MAX_INVITE_RETRY = 20;

    private final UserMapper userMapper;
    private final TeamMemberMapper teamMemberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamCreateResponseVO createTeam(TeamCreateRequest request) {
        validateCreateRequest(request);

        User owner = userMapper.selectById(request.getUserId());
        if (owner == null) {
            throw new BizException(400, "用户不存在");
        }

        Team team = new Team()
            .setName(request.getName().trim())
            .setDescription(request.getDescription() == null ? null : request.getDescription().trim())
            .setOwnerId(request.getUserId())
            .setInviteCode(generateUniqueInviteCode());
        save(team);

        TeamMember teamMember = new TeamMember()
            .setTeamId(team.getId())
            .setUserId(request.getUserId())
            .setRole(TeamMemberRoleEnum.CAPTAIN.getCode())
            .setJoinTime(LocalDateTime.now());
        teamMemberMapper.insert(teamMember);

        log.info("Team created successfully, teamId={}, ownerId={}", team.getId(), request.getUserId());
        return TeamCreateResponseVO.builder()
            .teamId(team.getId())
            .build();
    }

    private void validateCreateRequest(TeamCreateRequest request) {
        if (request == null) {
            throw new BizException(400, "请求参数不能为空");
        }
        if (request.getUserId() == null || request.getUserId() <= 0) {
            throw new BizException(400, "用户ID不合法");
        }
        if (isBlank(request.getName())) {
            throw new BizException(400, "小组名称不能为空");
        }
        String name = request.getName().trim();
        if (name.length() < 2 || name.length() > 50) {
            throw new BizException(400, "小组名称长度需在2到50之间");
        }
        if (request.getDescription() != null && request.getDescription().trim().length() > 300) {
            throw new BizException(400, "小组描述长度不能超过300");
        }
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < MAX_INVITE_RETRY; i++) {
            String code = randomInviteCode();
            boolean exists = exists(new LambdaQueryWrapper<Team>().eq(Team::getInviteCode, code));
            if (!exists) {
                return code;
            }
        }
        throw new BizException(500, "邀请码生成失败，请稍后重试");
    }

    private String randomInviteCode() {
        StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            builder.append(INVITE_CHARS.charAt(random.nextInt(INVITE_CHARS.length())));
        }
        return builder.toString();
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
