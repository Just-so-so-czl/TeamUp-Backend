package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.mapper.TeamMapper;
import com.czl.teamupbackend.mapper.TeamMessageMapper;
import com.czl.teamupbackend.model.dto.TeamMessageProcessRequest;
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
    public TeamMessageListVO listMyMessages(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(401, "未登录");
        }

        List<TeamMessage> messages = this.list(
            new LambdaQueryWrapper<TeamMessage>()
                .eq(TeamMessage::getUserId, userId)
                .orderByDesc(TeamMessage::getCreateTime)
        );
        if (messages.isEmpty()) {
            return TeamMessageListVO.builder()
                .allMessages(new ArrayList<>())
                .pendingMessages(new ArrayList<>())
                .build();
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
                .isProcessed(item.getIsProcessed())
                .build())
            .collect(Collectors.toList());

        List<TeamMessageItemVO> pendingMessages = allMessages.stream()
            .filter(item -> item.getIsProcessed() != null && item.getIsProcessed() == 0)
            .collect(Collectors.toList());

        return TeamMessageListVO.builder()
            .allMessages(allMessages)
            .pendingMessages(pendingMessages)
            .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processMessage(Long userId, TeamMessageProcessRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (request == null || request.getMessageId() == null || request.getMessageId() <= 0) {
            throw new BizException(400, "消息ID不合法");
        }

        TeamMessage message = this.getById(request.getMessageId());
        if (message == null) {
            throw new BizException(404, "消息不存在");
        }
        if (!userId.equals(message.getUserId())) {
            throw new BizException(403, "无权处理该消息");
        }
        if (message.getIsProcessed() != null && message.getIsProcessed() == 1) {
            return;
        }

        message.setIsProcessed(1);
        this.updateById(message);
        log.info("Message processed, messageId={}, userId={}", request.getMessageId(), userId);
    }
}
