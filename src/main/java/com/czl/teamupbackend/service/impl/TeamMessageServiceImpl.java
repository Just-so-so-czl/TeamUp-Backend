package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.mapper.TeamMessageMapper;
import com.czl.teamupbackend.model.entity.Team;
import com.czl.teamupbackend.model.entity.TeamMessage;
import com.czl.teamupbackend.model.vo.TeamMessageItemVO;
import com.czl.teamupbackend.model.vo.TeamMessageListVO;
import com.czl.teamupbackend.service.ITeamMessageService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamMessageServiceImpl extends ServiceImpl<TeamMessageMapper, TeamMessage> implements ITeamMessageService {

    private final TeamMapper teamMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamMessageListVO listMyMessages(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(401, "未登录");
        }

        List<TeamMessage> messages = this.list(
            new LambdaQueryWrapper<TeamMessage>()
                .eq(TeamMessage::getUserId, userId)
                .orderByDesc(TeamMessage::getCreateTime)
        );
        markUnreadMessagesAsRead(userId);
        if (messages.isEmpty()) {
            return TeamMessageListVO.builder().allMessages(new ArrayList<>()).build();
        }

        List<Long> teamIds = messages.stream()
            .map(TeamMessage::getTeamId)
            .distinct()
            .collect(Collectors.toList());
        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>().in(Team::getId, teamIds));
        Map<Long, String> teamNameMap = new HashMap<>();
        for (Team team : teams) {
            teamNameMap.put(team.getId(), team.getName());
        }

        List<TeamMessageItemVO> allMessages = messages.stream()
            .map(item -> TeamMessageItemVO.builder()
                .messageId(item.getId())
                .title(item.getTitle())
                .content(item.getContent())
                .teamName(teamNameMap.getOrDefault(item.getTeamId(), "未知小组"))
                .type(item.getType())
                .relatedUrl(item.getRelatedUrl())
                .messageTime(item.getCreateTime())
                .isRead(1)
                .build())
            .collect(Collectors.toList());

        return TeamMessageListVO.builder().allMessages(allMessages).build();
    }

    @Override
    public long countUnreadMessages(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(401, "未登录");
        }
        return this.count(new LambdaQueryWrapper<TeamMessage>()
            .eq(TeamMessage::getUserId, userId)
            .eq(TeamMessage::getIsRead, 0));
    }

    private void markUnreadMessagesAsRead(Long userId) {
        boolean updated = this.update(new LambdaUpdateWrapper<TeamMessage>()
            .eq(TeamMessage::getUserId, userId)
            .eq(TeamMessage::getIsRead, 0)
            .set(TeamMessage::getIsRead, 1));
        if (updated) {
            log.info("All unread messages marked as read, userId={}", userId);
        }
    }
}
