package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.mapper.TeamJoinRequestMapper;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.mapper.UserMapper;
import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.model.entity.TeamJoinRequest;
import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.model.enums.TeamMemberRoleEnum;
import com.czl.teamupbackend.model.vo.MyTeamListVO;
import com.czl.teamupbackend.model.vo.MyTeamVO;
import com.czl.teamupbackend.model.vo.TeamMemberBriefVO;
import com.czl.teamupbackend.model.vo.TeamMemberManageItemVO;
import com.czl.teamupbackend.model.vo.TeamMembersManageVO;
import com.czl.teamupbackend.model.vo.TeamPendingJoinRequestVO;
import com.czl.teamupbackend.service.ITeamMemberService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 小组成员关联表服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamMemberServiceImpl extends ServiceImpl<TeamMemberMapper, TeamMember> implements ITeamMemberService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;

    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final TeamJoinRequestMapper teamJoinRequestMapper;

    @Override
    public MyTeamListVO listMyTeams(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(400, "用户ID不合法");
        }

        List<TeamMember> selfMemberships = this.list(
            new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .orderByDesc(TeamMember::getJoinTime)
        );
        if (selfMemberships.isEmpty()) {
            return MyTeamListVO.builder().teams(new ArrayList<>()).build();
        }

        List<Long> teamIds = selfMemberships.stream()
            .map(TeamMember::getTeamId)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, Integer> selfRoleMap = selfMemberships.stream()
            .collect(Collectors.toMap(TeamMember::getTeamId, TeamMember::getRole, (a, b) -> a));

        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>().in(Team::getId, teamIds));
        Map<Long, Team> teamMap = teams.stream().collect(Collectors.toMap(Team::getId, team -> team));

        List<TeamMember> allMembers = this.list(new LambdaQueryWrapper<TeamMember>().in(TeamMember::getTeamId, teamIds));
        Map<Long, List<TeamMember>> membersByTeamId = allMembers.stream().collect(Collectors.groupingBy(TeamMember::getTeamId));

        List<Long> memberUserIds = allMembers.stream().map(TeamMember::getUserId).distinct().collect(Collectors.toList());
        List<User> users = memberUserIds.isEmpty()
            ? new ArrayList<>()
            : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, memberUserIds));
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        List<MyTeamVO> teamVOList = new ArrayList<>();
        for (Long teamId : teamIds) {
            Team team = teamMap.get(teamId);
            if (team == null) {
                continue;
            }
            List<TeamMember> memberList = membersByTeamId.getOrDefault(teamId, new ArrayList<>());
            List<TeamMemberBriefVO> memberVOList = memberList.stream()
                .sorted(Comparator.comparingInt(TeamMember::getRole).thenComparing(TeamMember::getJoinTime))
                .map(member -> {
                    User memberUser = userMap.get(member.getUserId());
                    TeamMemberRoleEnum roleEnum = TeamMemberRoleEnum.fromCode(member.getRole());
                    String username = memberUser == null ? "未知用户" : memberUser.getUsername();
                    return TeamMemberBriefVO.builder()
                        .userId(member.getUserId())
                        .username(username)
                        .roleCode(member.getRole())
                        .roleName(roleEnum.getRoleName())
                        .build();
                })
                .collect(Collectors.toList());

            Integer selfRoleCode = selfRoleMap.get(teamId);
            TeamMemberRoleEnum selfRole = TeamMemberRoleEnum.fromCode(selfRoleCode);
            teamVOList.add(MyTeamVO.builder()
                .teamId(teamId)
                .teamName(team.getName())
                .description(team.getDescription())
                .createTime(team.getCreateTime())
                .memberCount(memberList.size())
                .userRoleCode(selfRoleCode)
                .userRoleName(selfRole.getRoleName())
                .members(memberVOList)
                .build());
        }

        teamVOList.sort(Comparator.comparing(MyTeamVO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        log.info("Loaded my teams, userId={}, teamCount={}", userId, teamVOList.size());
        return MyTeamListVO.builder().teams(teamVOList).build();
    }

    @Override
    public TeamMembersManageVO getTeamMembersManage(Long currentUserId, Long teamId) {
        Team team = validateTeamAndMembership(currentUserId, teamId);

        List<TeamMember> members = this.list(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, teamId)
            .orderByAsc(TeamMember::getRole)
            .orderByAsc(TeamMember::getJoinTime));
        List<Long> memberUserIds = members.stream().map(TeamMember::getUserId).distinct().collect(Collectors.toList());

        List<TeamJoinRequest> approvedRequests = teamJoinRequestMapper.selectList(
            new LambdaQueryWrapper<TeamJoinRequest>()
                .eq(TeamJoinRequest::getTeamId, teamId)
                .eq(TeamJoinRequest::getStatus, STATUS_APPROVED)
                .orderByDesc(TeamJoinRequest::getUpdateTime)
        );
        Map<Long, LocalDateTime> joinTimeMap = new HashMap<>();
        for (TeamJoinRequest request : approvedRequests) {
            joinTimeMap.putIfAbsent(request.getUserId(), request.getUpdateTime());
        }

        List<TeamJoinRequest> pendingRequests = teamJoinRequestMapper.selectList(
            new LambdaQueryWrapper<TeamJoinRequest>()
                .eq(TeamJoinRequest::getTeamId, teamId)
                .eq(TeamJoinRequest::getStatus, STATUS_PENDING)
                .orderByDesc(TeamJoinRequest::getCreateTime)
        );
        List<Long> requestUserIds = pendingRequests.stream().map(TeamJoinRequest::getUserId).distinct().collect(Collectors.toList());

        List<Long> allUserIds = new ArrayList<>();
        allUserIds.addAll(memberUserIds);
        allUserIds.addAll(requestUserIds);
        allUserIds = allUserIds.stream().distinct().collect(Collectors.toList());

        Map<Long, User> userMap = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, allUserIds));
            userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
        }
        final Map<Long, User> finalUserMap = userMap;

        List<TeamMemberManageItemVO> memberVOList = members.stream().map(member -> {
            User user = finalUserMap.get(member.getUserId());
            TeamMemberRoleEnum roleEnum = TeamMemberRoleEnum.fromCode(member.getRole());
            LocalDateTime joinTime = joinTimeMap.getOrDefault(member.getUserId(), member.getJoinTime());
            return TeamMemberManageItemVO.builder()
                .userId(member.getUserId())
                .username(user == null ? "未知用户" : user.getUsername())
                .avatar(user == null ? 1 : user.getAvatar())
                .roleCode(member.getRole())
                .roleName(roleEnum.getRoleName())
                .roleDesc(roleEnum.getRoleDesc())
                .joinTime(joinTime)
                .build();
        }).collect(Collectors.toList());

        TeamMember selfMember = members.stream()
            .filter(item -> item.getUserId().equals(currentUserId))
            .findFirst()
            .orElse(null);
        TeamMemberRoleEnum selfRole = TeamMemberRoleEnum.fromCode(selfMember == null ? null : selfMember.getRole());

        List<TeamPendingJoinRequestVO> pendingVOList = pendingRequests.stream().map(req -> {
            User user = finalUserMap.get(req.getUserId());
            return TeamPendingJoinRequestVO.builder()
                .requestId(req.getId())
                .userId(req.getUserId())
                .username(user == null ? "未知用户" : user.getUsername())
                .avatar(user == null ? 1 : user.getAvatar())
                .description(req.getDescription())
                .createTime(req.getCreateTime())
                .build();
        }).collect(Collectors.toList());

        return TeamMembersManageVO.builder()
            .currentUserCaptain(team.getOwnerId().equals(currentUserId))
            .currentUserRoleName(selfRole.getRoleName())
            .currentUserRoleDesc(selfRole.getRoleDesc())
            .pendingRequests(pendingVOList)
            .members(memberVOList)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(Long currentUserId, Long teamId, Long memberUserId, Integer roleCode) {
        Team team = validateCaptainPermission(currentUserId, teamId);
        if (memberUserId == null || memberUserId <= 0) {
            throw new BizException(400, "成员用户ID不合法");
        }
        if (team.getOwnerId().equals(memberUserId)) {
            throw new BizException(400, "不能修改组长角色");
        }
        TeamMemberRoleEnum targetRole = TeamMemberRoleEnum.fromCode(roleCode);
        if (targetRole == TeamMemberRoleEnum.CAPTAIN) {
            throw new BizException(400, "不能将其他成员设为组长");
        }

        TeamMember targetMember = this.getOne(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, teamId)
            .eq(TeamMember::getUserId, memberUserId)
            .last("limit 1"));
        if (targetMember == null) {
            throw new BizException(404, "成员不存在");
        }

        targetMember.setRole(targetRole.getCode());
        this.updateById(targetMember);
        log.info("Member role updated, teamId={}, operatorUserId={}, memberUserId={}, roleCode={}",
            teamId, currentUserId, memberUserId, targetRole.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long currentUserId, Long teamId, Long memberUserId) {
        Team team = validateCaptainPermission(currentUserId, teamId);
        if (memberUserId == null || memberUserId <= 0) {
            throw new BizException(400, "成员用户ID不合法");
        }
        if (team.getOwnerId().equals(memberUserId)) {
            throw new BizException(400, "不能移除组长");
        }
        if (currentUserId.equals(memberUserId)) {
            throw new BizException(400, "不能移除自己");
        }

        boolean removed = this.remove(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, teamId)
            .eq(TeamMember::getUserId, memberUserId));
        if (!removed) {
            throw new BizException(404, "成员不存在");
        }
        log.info("Member removed, teamId={}, operatorUserId={}, memberUserId={}",
            teamId, currentUserId, memberUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSelfRoleDesc(Long currentUserId, Long teamId, String roleDesc) {
        Team team = validateTeamAndMembership(currentUserId, teamId);
        if (roleDesc != null && roleDesc.trim().length() > 80) {
            throw new BizException(400, "角色描述长度不能超过80");
        }
        TeamMember selfMember = this.getOne(new LambdaQueryWrapper<TeamMember>()
            .eq(TeamMember::getTeamId, teamId)
            .eq(TeamMember::getUserId, currentUserId)
            .last("limit 1"));
        if (selfMember == null) {
            throw new BizException(404, "成员不存在");
        }

        // 当前版本将描述保存到申请记录的 description 字段，作为用户在组中的个性描述。
        TeamJoinRequest latestApproved = teamJoinRequestMapper.selectOne(new LambdaQueryWrapper<TeamJoinRequest>()
            .eq(TeamJoinRequest::getTeamId, teamId)
            .eq(TeamJoinRequest::getUserId, currentUserId)
            .eq(TeamJoinRequest::getStatus, STATUS_APPROVED)
            .orderByDesc(TeamJoinRequest::getUpdateTime)
            .last("limit 1"));
        if (latestApproved != null) {
            latestApproved.setDescription(roleDesc == null ? null : roleDesc.trim());
            teamJoinRequestMapper.updateById(latestApproved);
        }
        log.info("Self role desc updated, teamId={}, userId={}", team.getId(), currentUserId);
    }

    private Team validateTeamAndMembership(Long currentUserId, Long teamId) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (teamId == null || teamId <= 0) {
            throw new BizException(400, "小组ID不能为空");
        }
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BizException(404, "小组不存在");
        }
        boolean isMember = this.count(
            new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, currentUserId)
        ) > 0;
        if (!isMember) {
            throw new BizException(403, "你不是该小组成员");
        }
        return team;
    }

    private Team validateCaptainPermission(Long currentUserId, Long teamId) {
        Team team = validateTeamAndMembership(currentUserId, teamId);
        if (!team.getOwnerId().equals(currentUserId)) {
            throw new BizException(403, "只有组长可执行该操作");
        }
        return team;
    }
}
