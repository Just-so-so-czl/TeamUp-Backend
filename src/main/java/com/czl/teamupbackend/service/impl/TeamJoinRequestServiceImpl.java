package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.mapper.TeamJoinRequestMapper;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.mapper.UserMapper;
import com.czl.teamupbackend.model.dto.TeamJoinRequestSubmitRequest;
import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.model.entity.TeamJoinRequest;
import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.model.entity.TeamMessage;
import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.model.enums.TeamMemberRoleEnum;
import com.czl.teamupbackend.model.vo.WebSocketNotifyVO;
import com.czl.teamupbackend.realtime.OnlineUserSessionManager;
import com.czl.teamupbackend.service.ITeamJoinRequestService;
import com.czl.teamupbackend.service.ITeamMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 入组申请服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamJoinRequestServiceImpl extends ServiceImpl<TeamJoinRequestMapper, TeamJoinRequest>
    implements ITeamJoinRequestService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;
    private static final int MESSAGE_TYPE_JOIN_REQUEST = 1;
    private static final int MESSAGE_UNPROCESSED = 0;

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ITeamMessageService teamMessageService;
    private final OnlineUserSessionManager onlineUserSessionManager;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitJoinRequest(Long userId, TeamJoinRequestSubmitRequest request) {
        validateRequest(userId, request);

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(400, "申请用户不存在");
        }

        String inviteCode = request.getInviteCode().trim();
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>().eq(Team::getInviteCode, inviteCode));
        if (team == null) {
            throw new BizException(400, "邀请码无效，请检查后重试");
        }
        if (team.getOwnerId().equals(userId)) {
            throw new BizException(400, "你已经是该小组组长");
        }

        boolean alreadyMember = teamMemberMapper.selectCount(
            new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId())
                .eq(TeamMember::getUserId, userId)
        ) > 0;
        if (alreadyMember) {
            throw new BizException(400, "你已加入该小组");
        }

        boolean pendingExists = this.count(
            new LambdaQueryWrapper<TeamJoinRequest>()
                .eq(TeamJoinRequest::getTeamId, team.getId())
                .eq(TeamJoinRequest::getUserId, userId)
                .eq(TeamJoinRequest::getStatus, STATUS_PENDING)
        ) > 0;
        if (pendingExists) {
            throw new BizException(400, "你已提交过申请，请等待组长处理");
        }

        String description = normalizeDescription(request.getDescription());

        TeamJoinRequest joinRequest = new TeamJoinRequest()
            .setUserId(userId)
            .setTeamId(team.getId())
            .setDescription(description)
            .setStatus(STATUS_PENDING);
        this.save(joinRequest);

        TeamMessage message = new TeamMessage()
            .setTitle("新的入组申请待处理")
            .setContent(buildMessageContent(user.getUsername(), team.getName(), description))
            .setTeamId(team.getId())
            .setType(MESSAGE_TYPE_JOIN_REQUEST)
            .setUserId(team.getOwnerId())
            .setRelatedUrl(buildMessageRelatedUrl(team.getId()))
            .setIsProcessed(MESSAGE_UNPROCESSED);
        teamMessageService.save(message);

        // 组长在线时推送实时提醒
        if (onlineUserSessionManager.isOnline(team.getOwnerId())) {
            WebSocketNotifyVO notifyVO = WebSocketNotifyVO.builder()
                .type("NEW_TEAM_MESSAGE")
                .title("新的入组申请")
                .content("你的小组“" + team.getName() + "”收到了新的入组申请")
                .teamId(team.getId())
                .build();
            try {
                String payload = objectMapper.writeValueAsString(notifyVO);
                onlineUserSessionManager.sendToUser(team.getOwnerId(), payload);
            } catch (Exception e) {
                log.warn("Build websocket notify payload failed, ownerId={}, error={}",
                    team.getOwnerId(), e.getMessage());
            }
        }

        log.info("Join request submitted, requestId={}, userId={}, teamId={}, ownerId={}",
            joinRequest.getId(), userId, team.getId(), team.getOwnerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveJoinRequest(Long operatorUserId, Long requestId) {
        TeamJoinRequest joinRequest = validateAndGetPendingRequest(operatorUserId, requestId);

        boolean alreadyMember = teamMemberMapper.selectCount(
            new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, joinRequest.getTeamId())
                .eq(TeamMember::getUserId, joinRequest.getUserId())
        ) > 0;
        if (!alreadyMember) {
            TeamMember teamMember = new TeamMember()
                .setTeamId(joinRequest.getTeamId())
                .setUserId(joinRequest.getUserId())
                .setRole(TeamMemberRoleEnum.MEMBER.getCode())
                .setJoinTime(LocalDateTime.now());
            teamMemberMapper.insert(teamMember);
        }

        joinRequest.setStatus(STATUS_APPROVED);
        this.updateById(joinRequest);
        log.info("Join request approved, requestId={}, operatorUserId={}", requestId, operatorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectJoinRequest(Long operatorUserId, Long requestId) {
        TeamJoinRequest joinRequest = validateAndGetPendingRequest(operatorUserId, requestId);
        joinRequest.setStatus(STATUS_REJECTED);
        this.updateById(joinRequest);
        log.info("Join request rejected, requestId={}, operatorUserId={}", requestId, operatorUserId);
    }

    private TeamJoinRequest validateAndGetPendingRequest(Long operatorUserId, Long requestId) {
        if (operatorUserId == null || operatorUserId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (requestId == null || requestId <= 0) {
            throw new BizException(400, "申请ID不能为空");
        }

        TeamJoinRequest joinRequest = this.getById(requestId);
        if (joinRequest == null) {
            throw new BizException(404, "申请不存在");
        }
        if (joinRequest.getStatus() == null || joinRequest.getStatus() != STATUS_PENDING) {
            throw new BizException(400, "申请已处理，无需重复操作");
        }

        Team team = teamMapper.selectById(joinRequest.getTeamId());
        if (team == null) {
            throw new BizException(404, "小组不存在");
        }
        if (!team.getOwnerId().equals(operatorUserId)) {
            throw new BizException(403, "只有组长可操作入组申请");
        }
        return joinRequest;
    }

    private void validateRequest(Long userId, TeamJoinRequestSubmitRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (request == null) {
            throw new BizException(400, "请求参数不能为空");
        }
        if (request.getInviteCode() == null || request.getInviteCode().trim().isEmpty()) {
            throw new BizException(400, "邀请码不能为空");
        }
        if (request.getInviteCode().trim().length() > 20) {
            throw new BizException(400, "邀请码长度不能超过20");
        }
        if (request.getDescription() != null && request.getDescription().trim().length() > 500) {
            throw new BizException(400, "备注长度不能超过500");
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildMessageContent(String username, String teamName, String description) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户【").append(username).append("】申请加入小组【").append(teamName).append("】。");
        if (description != null) {
            builder.append(" 备注：").append(description);
        }
        return builder.toString();
    }

    private String buildMessageRelatedUrl(Long teamId) {
        if (teamId == null) {
            return "/messages";
        }
        return "/teams/" + teamId + "?tab=members";
    }
}
