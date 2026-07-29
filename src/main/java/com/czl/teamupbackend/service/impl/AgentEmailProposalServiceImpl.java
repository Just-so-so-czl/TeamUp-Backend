package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.event.AgentConfirmationCompletedEvent;
import com.czl.teamupbackend.event.AgentProposalRejectedEvent;
import com.czl.teamupbackend.mapper.AiActionDraftMapper;
import com.czl.teamupbackend.mapper.TeamMemberMapper;
import com.czl.teamupbackend.mapper.UserMapper;
import com.czl.teamupbackend.model.dto.AiEmailProposalToolRequest;
import com.czl.teamupbackend.model.entity.AiActionDraft;
import com.czl.teamupbackend.model.entity.TeamMember;
import com.czl.teamupbackend.model.entity.User;
import com.czl.teamupbackend.model.enums.TeamMemberRoleEnum;
import com.czl.teamupbackend.model.vo.AgentEmailProposalVO;
import com.czl.teamupbackend.service.AgentEmailProposalService;
import com.czl.teamupbackend.service.AgentRunService;
import com.czl.teamupbackend.service.TeamMailService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEmailProposalServiceImpl implements AgentEmailProposalService {
    private static final String ACTION_EMAIL_SEND = "EMAIL_SEND";
    private static final String STATUS_PENDING = "PENDING_CONFIRMATION";
    private static final String STATUS_SENDING = "SENDING";
    private static final String STATUS_EXECUTED = "EXECUTED";
    private static final String STATUS_REJECTED = "REJECTED";
    private final AiActionDraftMapper draftMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final TeamMailService teamMailService;
    private final AgentRunService agentRunService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public AgentEmailProposalVO create(Long runId, Long operatorUserId, Long teamId, AiEmailProposalToolRequest request) {
        if (runId == null || operatorUserId == null || teamId == null || request == null || request.getRecipientUserId() == null) {
            throw new BizException(400, "邮件提案参数不完整");
        }
        if (hasExecuted(operatorUserId, runId)) {
            throw new BizException(409, "当前运行已成功发送邮件，不能重复生成同类草案");
        }
        TeamMember operator = member(teamId, operatorUserId);
        TeamMemberRoleEnum role = TeamMemberRoleEnum.fromCode(operator.getRole());
        if (role != TeamMemberRoleEnum.CAPTAIN && role != TeamMemberRoleEnum.LEADER) {
            throw new BizException(403, "只有Captain或Leader可以发起组员邮件提案");
        }
        TeamMember recipientMember = member(teamId, request.getRecipientUserId());
        User recipient = userMapper.selectById(recipientMember.getUserId());
        if (recipient == null || blank(recipient.getEmail())) {
            throw new BizException(409, "收件成员未配置邮箱");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientUserId", String.valueOf(recipient.getId()));
        payload.put("recipientName", safe(recipient.getUsername()));
        payload.put("recipientEmail", recipient.getEmail());
        payload.put("subject", validateSubject(request.getSubject()));
        payload.put("content", validateContent(request.getContent()));
        AiActionDraft draft = new AiActionDraft().setRunId(runId).setTeamId(teamId).setCreatorUserId(operatorUserId)
            .setActionType(ACTION_EMAIL_SEND).setStatus(STATUS_PENDING).setPayloadJson(writePayload(payload))
            .setResultSummary("").setErrorMsg("").setCreatedAt(LocalDateTime.now());
        draftMapper.insert(draft);
        agentRunService.awaitConfirmation(runId, "已生成给“" + safe(recipient.getUsername()) + "”的邮件草案，等待用户确认发送");
        log.info("Email proposal created, draftId={}, runId={}, teamId={}, recipientUserId={}",
            draft.getId(), runId, teamId, recipient.getId());
        return toVo(draft, payload);
    }

    @Override
    public AgentEmailProposalVO getPending(Long operatorUserId, Long runId) {
        AiActionDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<AiActionDraft>()
            .eq(AiActionDraft::getRunId, runId).eq(AiActionDraft::getCreatorUserId, operatorUserId)
            .eq(AiActionDraft::getActionType, ACTION_EMAIL_SEND)
            .eq(AiActionDraft::getStatus, STATUS_PENDING)
            .orderByDesc(AiActionDraft::getCreatedAt)
            .orderByDesc(AiActionDraft::getId)
            .last("LIMIT 1"));
        return draft == null ? null : toVo(draft, readPayload(draft));
    }

    @Override
    public boolean hasExecuted(Long operatorUserId, Long runId) {
        if (operatorUserId == null || runId == null) return false;
        return draftMapper.selectCount(new LambdaQueryWrapper<AiActionDraft>()
            .eq(AiActionDraft::getRunId, runId)
            .eq(AiActionDraft::getCreatorUserId, operatorUserId)
            .eq(AiActionDraft::getActionType, ACTION_EMAIL_SEND)
            .eq(AiActionDraft::getStatus, STATUS_EXECUTED)) > 0;
    }

    @Override
    public AgentEmailProposalVO execute(Long operatorUserId, Long draftId, String subject, String content) {
        AiActionDraft draft = draftMapper.selectById(draftId);
        if (draft == null || !operatorUserId.equals(draft.getCreatorUserId()) || !ACTION_EMAIL_SEND.equals(draft.getActionType())) {
            throw new BizException(404, "邮件提案不存在或无权限");
        }
        if (STATUS_EXECUTED.equals(draft.getStatus())) return toVo(draft, readPayload(draft));
        log.info("Email proposal execution started, draftId={}, runId={}, operatorUserId={}", draftId, draft.getRunId(), operatorUserId);
        boolean claimed = draftMapper.update(null, new LambdaUpdateWrapper<AiActionDraft>()
            .eq(AiActionDraft::getId, draftId).eq(AiActionDraft::getStatus, STATUS_PENDING)
            .set(AiActionDraft::getStatus, STATUS_SENDING).set(AiActionDraft::getUpdatedAt, LocalDateTime.now())) == 1;
        if (!claimed) throw new BizException(409, "该邮件正在发送或已处理，请勿重复提交");
        Map<String, Object> payload = readPayload(draft);
        payload.put("subject", validateSubject(subject));
        payload.put("content", validateContent(content));
        try {
            teamMailService.sendPlainMail(String.valueOf(payload.get("recipientEmail")), String.valueOf(payload.get("subject")), String.valueOf(payload.get("content")));
            String summary = "已向“" + payload.get("recipientName") + "”发送邮件";
            draftMapper.updateById(new AiActionDraft().setId(draftId).setStatus(STATUS_EXECUTED).setPayloadJson(writePayload(payload))
                .setResultSummary(summary).setErrorMsg("").setExecutedAt(LocalDateTime.now()));
            agentRunService.resumeAfterConfirmedWrite(draft.getRunId(), summary);
            log.info("Email proposal publishing agent confirmation event, draftId={}, runId={}", draftId, draft.getRunId());
            applicationEventPublisher.publishEvent(new AgentConfirmationCompletedEvent(draft.getRunId(), operatorUserId, "sendTeamEmail", summary));
            draft.setStatus(STATUS_EXECUTED); draft.setPayloadJson(writePayload(payload)); draft.setResultSummary(summary);
            log.info("Email proposal executed, draftId={}, runId={}", draftId, draft.getRunId());
            return toVo(draft, payload);
        } catch (RuntimeException ex) {
            log.error("Email proposal execution failed, draftId={}, runId={}", draftId, draft.getRunId(), ex);
            draftMapper.updateById(new AiActionDraft().setId(draftId).setStatus(STATUS_PENDING).setErrorMsg(limit(ex.getMessage(), 500)));
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentEmailProposalVO reject(Long operatorUserId, Long draftId) {
        AiActionDraft draft = draftMapper.selectById(draftId);
        if (draft == null || !operatorUserId.equals(draft.getCreatorUserId())
            || !ACTION_EMAIL_SEND.equals(draft.getActionType())) {
            throw new BizException(404, "邮件提案不存在或无权限");
        }
        Map<String, Object> payload = readPayload(draft);
        if (STATUS_REJECTED.equals(draft.getStatus())) {
            return toVo(draft, payload);
        }
        if (!STATUS_PENDING.equals(draft.getStatus())) {
            throw new BizException(409, "该邮件草案正在处理或已完成，无法拒绝");
        }
        LocalDateTime now = LocalDateTime.now();
        String summary = "用户已拒绝邮件草案，邮件未发送，已停止后续任务";
        int updated = draftMapper.update(null, new LambdaUpdateWrapper<AiActionDraft>()
            .eq(AiActionDraft::getId, draftId)
            .eq(AiActionDraft::getStatus, STATUS_PENDING)
            .set(AiActionDraft::getStatus, STATUS_REJECTED)
            .set(AiActionDraft::getResultSummary, summary)
            .set(AiActionDraft::getErrorMsg, "")
            .set(AiActionDraft::getExecutedAt, now)
            .set(AiActionDraft::getUpdatedAt, now));
        if (updated != 1) {
            throw new BizException(409, "该邮件草案状态已变化，请刷新后重试");
        }
        agentRunService.resumeAfterRejectedDecision(draft.getRunId(), "proposeTeamEmail", summary);
        applicationEventPublisher.publishEvent(new AgentProposalRejectedEvent(
            draft.getRunId(), operatorUserId, "proposeTeamEmail", summary));
        draft.setStatus(STATUS_REJECTED).setResultSummary(summary).setErrorMsg("")
            .setExecutedAt(now).setUpdatedAt(now);
        log.info("Email proposal rejected, draftId={}, runId={}, operatorUserId={}",
            draftId, draft.getRunId(), operatorUserId);
        return toVo(draft, payload);
    }

    @Override
    public Map<Long, AgentEmailProposalVO> findByRunIds(Long operatorUserId, Collection<Long> runIds) {
        if (runIds == null || runIds.isEmpty()) return Map.of();
        List<AiActionDraft> drafts = draftMapper.selectList(new LambdaQueryWrapper<AiActionDraft>()
            .eq(AiActionDraft::getCreatorUserId, operatorUserId).eq(AiActionDraft::getActionType, ACTION_EMAIL_SEND)
            .in(AiActionDraft::getRunId, runIds)
            .orderByDesc(AiActionDraft::getCreatedAt)
            .orderByDesc(AiActionDraft::getId));
        Map<Long, AgentEmailProposalVO> result = new LinkedHashMap<>();
        for (AiActionDraft draft : drafts) {
            result.putIfAbsent(draft.getRunId(), toVo(draft, readPayload(draft)));
        }
        return result;
    }

    private TeamMember member(Long teamId, Long userId) {
        TeamMember member = teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId).last("LIMIT 1"));
        if (member == null) throw new BizException(403, "目标用户不是当前小组成员");
        return member;
    }
    private String validateSubject(String text) { String value = safe(text).trim(); if (value.isEmpty() || value.length() > 150) throw new BizException(400, "邮件主题长度需在1到150个字符之间"); return value; }
    private String validateContent(String text) { String value = safe(text).trim(); if (value.isEmpty() || value.length() > 5000) throw new BizException(400, "邮件正文长度需在1到5000个字符之间"); return value; }
    private String writePayload(Map<String, Object> payload) { try { return objectMapper.writeValueAsString(payload); } catch (Exception e) { throw new BizException(500, "保存邮件提案失败"); } }
    private Map<String, Object> readPayload(AiActionDraft draft) { try { return objectMapper.readValue(draft.getPayloadJson(), new TypeReference<>() {}); } catch (Exception e) { throw new BizException(500, "邮件提案数据损坏"); } }
    private AgentEmailProposalVO toVo(AiActionDraft draft, Map<String, Object> payload) { return AgentEmailProposalVO.builder().draftId(String.valueOf(draft.getId())).runId(String.valueOf(draft.getRunId())).status(draft.getStatus()).recipientUserId(String.valueOf(payload.get("recipientUserId"))).recipientName(String.valueOf(payload.get("recipientName"))).recipientEmail(String.valueOf(payload.get("recipientEmail"))).subject(String.valueOf(payload.get("subject"))).content(String.valueOf(payload.get("content"))).resultSummary(draft.getResultSummary()).build(); }
    private String safe(String text) { return text == null ? "" : text; }
    private boolean blank(String text) { return safe(text).trim().isEmpty(); }
    private String limit(String text, int max) { String value = safe(text); return value.length() <= max ? value : value.substring(0, max); }
}
