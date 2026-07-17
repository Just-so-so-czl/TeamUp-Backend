package com.czl.teamupbackend.model.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorChatMessageItemVO {

    private String messageId;

    private String senderType;

    private String messageType;

    private String content;

    private LocalDateTime createdAt;

    private String agentRunId;

    private String agentStatus;

    private List<MentorAgentStepVO> agentSteps;

    private AgentEmailProposalVO emailProposal;

    private AgentTaskListProposalVO taskListProposal;
}
