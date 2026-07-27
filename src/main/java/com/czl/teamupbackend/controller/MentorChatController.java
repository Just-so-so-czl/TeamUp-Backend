package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.MentorChatHistoryRequest;
import com.czl.teamupbackend.model.dto.MentorChatRequest;
import com.czl.teamupbackend.model.dto.MentorAgentRunSubscribeRequest;
import com.czl.teamupbackend.model.dto.MentorCreateSessionRequest;
import com.czl.teamupbackend.model.dto.MentorSessionListRequest;
import com.czl.teamupbackend.mapper.AiAgentRunMapper;
import com.czl.teamupbackend.model.entity.AiAgentRun;
import com.czl.teamupbackend.model.vo.MentorChatHistoryVO;
import com.czl.teamupbackend.model.vo.MentorSessionItemVO;
import com.czl.teamupbackend.model.vo.MentorSessionListVO;
import com.czl.teamupbackend.service.IMentorChatService;
import com.czl.teamupbackend.service.AgentRunService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/ai/mentor")
@RequiredArgsConstructor
public class MentorChatController {

    private final IMentorChatService mentorChatService;
    private final AgentRunService agentRunService;
    private final AiAgentRunMapper agentRunMapper;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamChat(@RequestBody MentorChatRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return streamResponse(errorEmitter("unauthorized"));
        }
        log.info("Mentor stream request userId={}, teamId={}, sessionId={}",
            userId, request == null ? null : request.getTeamId(), request == null ? null : request.getSessionId());
        try {
            return streamResponse(mentorChatService.streamChat(userId, request));
        } catch (BizException e) {
            log.warn("Mentor stream rejected: {}", e.getMessage());
            return streamResponse(errorEmitter(e.getMessage()));
        }
    }

    @PostMapping(value = "/run/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribeRun(@RequestBody MentorAgentRunSubscribeRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null || request == null || request.getRunId() == null) {
            return streamResponse(errorEmitter("unauthorized"));
        }
        AiAgentRun run = agentRunMapper.selectById(request.getRunId());
        if (run == null || !userId.equals(run.getUserId())) {
            return streamResponse(errorEmitter("agent run not found or unauthorized"));
        }

        Long runId = run.getId();
        SseEmitter emitter = new SseEmitter(0L);
        Consumer<AgentRunService.AgentRunProgress> listener = progress -> sendAgentProgress(emitter, progress);
        agentRunService.addListener(runId, listener);
        Runnable unsubscribe = () -> agentRunService.removeListener(runId, listener);
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(error -> unsubscribe.run());
        log.info("Mentor agent run subscription opened, userId={}, runId={}, runStatus={}", userId, runId, run.getStatus());
        CompletableFuture.runAsync(() -> replayRunProgress(emitter, runId));
        return streamResponse(emitter);
    }

    private void replayRunProgress(SseEmitter emitter, Long runId) {
        try {
            var snapshot = agentRunService.getProgressSnapshot(runId);
            for (AgentRunService.AgentRunProgress progress : snapshot) {
                emitter.send(SseEmitter.event().name("agent-status").data(objectMapper.writeValueAsString(progress)));
            }
            emitter.send(SseEmitter.event().name("ready").data("[READY]"));
            boolean terminal = snapshot.stream().anyMatch(progress -> "FINISH".equals(progress.stepType())
                && ("COMPLETED".equals(progress.status()) || "FAILED".equals(progress.status())));
            if (terminal) {
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            }
        } catch (Exception e) {
            log.warn("Replay mentor agent run subscription failed, runId={}", runId, e);
            emitter.completeWithError(e);
        }
    }

    private void sendAgentProgress(SseEmitter emitter, AgentRunService.AgentRunProgress progress) {
        try {
            emitter.send(SseEmitter.event().name("agent-status").data(objectMapper.writeValueAsString(progress)));
            if ("FINISH".equals(progress.stepType())
                && ("COMPLETED".equals(progress.status()) || "FAILED".equals(progress.status()))) {
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            }
        } catch (Exception e) {
            log.info("Mentor agent run subscription closed while sending status, runId={}", progress.runId());
            emitter.completeWithError(e);
        }
    }

    private ResponseEntity<SseEmitter> streamResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .cacheControl(CacheControl.noCache())
            .header("X-Accel-Buffering", "no")
            .header("Connection", "keep-alive")
            .body(emitter);
    }

    private SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(5_000L);
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("error").data(message == null ? "stream error" : message));
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PostMapping("/session/list")
    public Result<MentorSessionListVO> listSessions(@RequestBody MentorSessionListRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "unauthorized");
        }
        log.info("Mentor session list request userId={}, teamId={}", userId, request == null ? null : request.getTeamId());
        return Result.success(mentorChatService.listSessions(userId, request));
    }

    @PostMapping("/history")
    public Result<MentorChatHistoryVO> history(@RequestBody MentorChatHistoryRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "unauthorized");
        }
        return Result.success(mentorChatService.getHistory(userId, request));
    }

    @PostMapping("/session/create")
    public Result<MentorSessionItemVO> createSession(@RequestBody MentorCreateSessionRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "unauthorized");
        }
        return Result.success(mentorChatService.createSession(userId, request));
    }
}
