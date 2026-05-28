package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.MentorChatHistoryRequest;
import com.czl.teamupbackend.model.dto.MentorChatRequest;
import com.czl.teamupbackend.model.dto.MentorCreateSessionRequest;
import com.czl.teamupbackend.model.dto.MentorSessionListRequest;
import com.czl.teamupbackend.model.vo.MentorChatHistoryVO;
import com.czl.teamupbackend.model.vo.MentorSessionItemVO;
import com.czl.teamupbackend.model.vo.MentorSessionListVO;
import com.czl.teamupbackend.service.IMentorChatService;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
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
