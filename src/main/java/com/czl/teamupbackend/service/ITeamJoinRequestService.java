package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.entity.TeamJoinRequest;
import com.czl.teamupbackend.model.dto.TeamJoinRequestSubmitRequest;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 加入小组请求表 服务类
 * </p>
 *
 * @author czl
 * @since 2026-04-21
 */
public interface ITeamJoinRequestService extends IService<TeamJoinRequest> {

    /**
     * 提交加入小组申请
     *
     * @param request 请求参数
     */
    void submitJoinRequest(Long userId, TeamJoinRequestSubmitRequest request);

    /**
     * 同意入组申请
     *
     * @param operatorUserId 当前操作用户（组长）
     * @param requestId 申请ID
     */
    void approveJoinRequest(Long operatorUserId, Long requestId);

    /**
     * 拒绝入组申请
     *
     * @param operatorUserId 当前操作用户（组长）
     * @param requestId 申请ID
     */
    void rejectJoinRequest(Long operatorUserId, Long requestId);
}
