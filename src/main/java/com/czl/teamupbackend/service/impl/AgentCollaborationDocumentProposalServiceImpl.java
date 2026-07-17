package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.exception.CollaborationDocumentPatchValidationException;
import com.czl.teamupbackend.mapper.AiActionDraftMapper;
import com.czl.teamupbackend.mapper.DocumentMapper;
import com.czl.teamupbackend.model.dto.AiCollaborationDocumentPatchToolRequest;
import com.czl.teamupbackend.model.entity.AiActionDraft;
import com.czl.teamupbackend.model.entity.Document;
import com.czl.teamupbackend.model.mongo.AgentCollaborationSnapshotDoc;
import com.czl.teamupbackend.model.vo.AgentCollaborationDocumentPatchChangeVO;
import com.czl.teamupbackend.model.vo.AgentCollaborationDocumentPatchProposalVO;
import com.czl.teamupbackend.repository.AgentCollaborationSnapshotRepository;
import com.czl.teamupbackend.service.AgentCollaborationDocumentProposalService;
import com.czl.teamupbackend.service.AgentRunService;
import com.czl.teamupbackend.service.CollaborationAgentDocumentGateway;
import com.czl.teamupbackend.service.ITeamService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCollaborationDocumentProposalServiceImpl implements AgentCollaborationDocumentProposalService {

    private static final int COLLAB_DOCUMENT_TYPE = 2;
    private static final String ACTION_TYPE = "COLLAB_DOCUMENT_PATCH";
    private static final String PENDING = "PENDING_CONFIRMATION";
    private static final String APPLYING = "APPLYING";
    private static final String EXECUTED = "EXECUTED";
    private static final String CONFLICTED = "CONFLICTED";

    private final AgentCollaborationSnapshotRepository snapshotRepository;
    private final AiActionDraftMapper draftMapper;
    private final DocumentMapper documentMapper;
    private final ITeamService teamService;
    private final CollaborationAgentDocumentGateway documentGateway;
    private final AgentRunService agentRunService;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> captureForAgent(Long runId, Long userId, Long teamId, Long documentId) {
        validateContext(runId, userId, teamId, documentId);
        Map<String, Object> source = documentGateway.captureSnapshot(documentId);
        AgentCollaborationSnapshotDoc snapshot = new AgentCollaborationSnapshotDoc();
        snapshot.setId(UUID.randomUUID().toString().replace("-", ""));
        snapshot.setRunId(runId);
        snapshot.setTeamId(teamId);
        snapshot.setDocumentId(documentId);
        snapshot.setCreatorUserId(userId);
        snapshot.setBaseContentHash(stringValue(source.get("contentHash")));
        snapshot.setBaseStateVector(stringValue(source.get("stateVector")));
        snapshot.setPlainText(stringValue(source.get("plainText")));
        snapshot.setContentJson(toMap(source.get("contentJson")));
        snapshot.setBlocks(toListOfMaps(source.get("blocks")));
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshotRepository.save(snapshot);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotId", snapshot.getId());
        result.put("documentId", String.valueOf(documentId));
        result.put("contentHash", snapshot.getBaseContentHash());
        result.put("blocks", snapshot.getBlocks());
        result.put("plainText", snapshot.getPlainText());
        return result;
    }

    @Override
    public AgentCollaborationDocumentPatchProposalVO create(Long runId, Long userId, Long teamId, Long documentId,
                                                              AiCollaborationDocumentPatchToolRequest request) {
        validateContext(runId, userId, teamId, documentId);
        if (request == null || request.getSnapshotId() == null || request.getSnapshotId().isBlank()) {
            throw new BizException(400, "文档编辑草案缺少 snapshotId");
        }
        AgentCollaborationSnapshotDoc snapshot = snapshotRepository.findById(request.getSnapshotId())
            .orElseThrow(() -> new BizException(404, "文档快照不存在或已过期"));
        if (!runId.equals(snapshot.getRunId()) || !userId.equals(snapshot.getCreatorUserId())
            || !teamId.equals(snapshot.getTeamId()) || !documentId.equals(snapshot.getDocumentId())) {
            throw new BizException(403, "文档快照不属于当前 Agent 运行上下文");
        }
        List<Map<String, Object>> operations = objectMapper.convertValue(request.getOperations(), new TypeReference<>() { });
        CollaborationAgentDocumentGateway.PreviewResult previewResult = documentGateway.preview(snapshot.getContentJson(), operations);
        if (!previewResult.valid()) {
            throw new CollaborationDocumentPatchValidationException(previewResult.validationMessage());
        }
        Map<String, Object> preview = previewResult.payload();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", String.valueOf(documentId));
        payload.put("snapshotId", snapshot.getId());
        payload.put("baseContentHash", snapshot.getBaseContentHash());
        payload.put("operations", operations);
        payload.put("changes", toListOfMaps(preview.get("changes")));
        payload.put("changeSummary", stringValue(preview.get("changeSummary")));

        AiActionDraft draft = new AiActionDraft()
            .setRunId(runId)
            .setTeamId(teamId)
            .setCreatorUserId(userId)
            .setActionType(ACTION_TYPE)
            .setStatus(PENDING)
            .setPayloadJson(write(payload))
            .setResultSummary("")
            .setErrorMsg("")
            .setCreatedAt(LocalDateTime.now());
        draftMapper.insert(draft);
        agentRunService.awaitConfirmation(runId, "proposeCollaborationDocumentPatch", "已生成协作文档编辑草案，等待用户审核并应用");
        log.info("Collaboration document patch draft created, draftId={}, runId={}, documentId={}, changeCount={}",
            draft.getId(), runId, documentId, toListOfMaps(preview.get("changes")).size());
        return toVo(draft, payload);
    }

    @Override
    public AgentCollaborationDocumentPatchProposalVO getPending(Long userId, Long runId) {
        if (userId == null || runId == null) {
            return null;
        }
        AiActionDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<AiActionDraft>()
            .eq(AiActionDraft::getRunId, runId)
            .eq(AiActionDraft::getCreatorUserId, userId)
            .eq(AiActionDraft::getActionType, ACTION_TYPE)
            .last("LIMIT 1"));
        return draft == null ? null : toVo(draft, read(draft));
    }

    @Override
    public AgentCollaborationDocumentPatchProposalVO execute(Long userId, Long draftId) {
        AiActionDraft draft = draftMapper.selectById(draftId);
        if (draft == null || !userId.equals(draft.getCreatorUserId()) || !ACTION_TYPE.equals(draft.getActionType())) {
            throw new BizException(404, "协作文档草案不存在或无权限");
        }
        Map<String, Object> payload = read(draft);
        if (EXECUTED.equals(draft.getStatus()) || CONFLICTED.equals(draft.getStatus())) {
            return toVo(draft, payload);
        }
        if (draftMapper.update(null, new LambdaUpdateWrapper<AiActionDraft>()
            .eq(AiActionDraft::getId, draftId)
            .eq(AiActionDraft::getStatus, PENDING)
            .set(AiActionDraft::getStatus, APPLYING)) != 1) {
            throw new BizException(409, "该文档草案正在处理或已失效");
        }

        Long documentId = Long.valueOf(stringValue(payload.get("documentId")));
        validateContext(draft.getRunId(), userId, draft.getTeamId(), documentId);
        CollaborationAgentDocumentGateway.ApplyResult result = documentGateway.apply(
            documentId,
            stringValue(payload.get("baseContentHash")),
            toListOfMaps(payload.get("operations"))
        );
        if (!result.applied()) {
            draftMapper.updateById(new AiActionDraft().setId(draftId).setStatus(CONFLICTED).setErrorMsg(result.conflictMessage()));
            draft.setStatus(CONFLICTED);
            draft.setErrorMsg(result.conflictMessage());
            log.info("Collaboration document patch conflicted, draftId={}, documentId={}, message={}",
                draftId, documentId, result.conflictMessage());
            return toVo(draft, payload);
        }
        draftMapper.updateById(new AiActionDraft()
            .setId(draftId)
            .setStatus(EXECUTED)
            .setResultSummary(result.resultSummary())
            .setExecutedAt(LocalDateTime.now()));
        agentRunService.resumeAfterConfirmedWrite(draft.getRunId(), "applyCollaborationDocumentPatch", result.resultSummary());
        draft.setStatus(EXECUTED);
        draft.setResultSummary(result.resultSummary());
        return toVo(draft, payload);
    }

    @Override
    public Map<Long, AgentCollaborationDocumentPatchProposalVO> findByRunIds(Long userId, Collection<Long> runIds) {
        if (userId == null || runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        return draftMapper.selectList(new LambdaQueryWrapper<AiActionDraft>()
                .eq(AiActionDraft::getCreatorUserId, userId)
                .eq(AiActionDraft::getActionType, ACTION_TYPE)
                .in(AiActionDraft::getRunId, runIds))
            .stream()
            .collect(Collectors.toMap(AiActionDraft::getRunId, draft -> toVo(draft, read(draft)), (newer, older) -> newer));
    }

    private void validateContext(Long runId, Long userId, Long teamId, Long documentId) {
        if (runId == null || userId == null || teamId == null || documentId == null) {
            throw new BizException(400, "协作文档 Agent 上下文不完整");
        }
        teamService.validateTeamAccessible(userId, teamId);
        Document document = documentMapper.selectById(documentId);
        if (document == null || !teamId.equals(document.getTeamId()) || !Integer.valueOf(COLLAB_DOCUMENT_TYPE).equals(document.getType())) {
            throw new BizException(404, "协作文档不存在或不属于当前小组");
        }
    }

    private AgentCollaborationDocumentPatchProposalVO toVo(AiActionDraft draft, Map<String, Object> payload) {
        return AgentCollaborationDocumentPatchProposalVO.builder()
            .draftId(String.valueOf(draft.getId()))
            .runId(String.valueOf(draft.getRunId()))
            .status(draft.getStatus())
            .documentId(stringValue(payload.get("documentId")))
            .changeSummary(stringValue(payload.get("changeSummary")))
            .changes(toListOfMaps(payload.get("changes")).stream().map(this::toChangeVo).toList())
            .resultSummary(draft.getResultSummary())
            .errorMsg(draft.getErrorMsg())
            .build();
    }

    private AgentCollaborationDocumentPatchChangeVO toChangeVo(Map<String, Object> change) {
        return AgentCollaborationDocumentPatchChangeVO.builder()
            .operation(stringValue(change.get("operation")))
            .targetBlockId(stringValue(change.get("targetBlockId")))
            .beforeText(stringValue(change.get("beforeText")))
            .afterText(stringValue(change.get("afterText")))
            .reason(stringValue(change.get("reason")))
            .build();
    }

    private String write(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new BizException(500, "保存协作文档草案失败");
        }
    }

    private Map<String, Object> read(AiActionDraft draft) {
        try {
            return objectMapper.readValue(draft.getPayloadJson(), new TypeReference<>() { });
        } catch (Exception exception) {
            throw new BizException(500, "协作文档草案数据损坏");
        }
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() { });
    }

    private List<Map<String, Object>> toListOfMaps(Object value) {
        return value == null ? List.of() : objectMapper.convertValue(value, new TypeReference<>() { });
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
