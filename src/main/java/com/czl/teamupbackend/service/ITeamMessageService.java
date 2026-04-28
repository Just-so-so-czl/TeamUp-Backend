package com.czl.teamupbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.czl.teamupbackend.model.dto.TeamMessageProcessRequest;
import com.czl.teamupbackend.model.entity.TeamMessage;
import com.czl.teamupbackend.model.vo.TeamMessageListVO;

/**
 * 消息表服务
 */
public interface ITeamMessageService extends IService<TeamMessage> {

    TeamMessageListVO listMyMessages(Long userId);

    void processMessage(Long userId, TeamMessageProcessRequest request);
}
