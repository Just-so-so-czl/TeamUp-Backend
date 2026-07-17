package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.dto.MentorChatHistoryRequest;
import com.czl.teamupbackend.model.dto.MentorChatRequest;
import com.czl.teamupbackend.model.dto.MentorCreateSessionRequest;
import com.czl.teamupbackend.model.dto.MentorSessionListRequest;
import com.czl.teamupbackend.model.vo.MentorChatHistoryVO;
import com.czl.teamupbackend.model.vo.MentorSessionItemVO;
import com.czl.teamupbackend.model.vo.MentorSessionListVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IMentorChatService {

    SseEmitter streamChat(Long userId, MentorChatRequest request);

    MentorSessionListVO listSessions(Long userId, MentorSessionListRequest request);

    MentorChatHistoryVO getHistory(Long userId, MentorChatHistoryRequest request);

    MentorSessionItemVO createSession(Long userId, MentorCreateSessionRequest request);

    void resumeAfterConfirmation(Long runId, Long userId, String toolName, String resultSummary);
}
