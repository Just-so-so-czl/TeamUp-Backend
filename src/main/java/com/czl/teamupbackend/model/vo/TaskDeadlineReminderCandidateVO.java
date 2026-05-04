package com.czl.teamupbackend.model.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TaskDeadlineReminderCandidateVO {

    private Long taskId;

    private Long assigneeUserId;

    private String assigneeEmail;

    private String assigneeName;

    private String taskDescription;

    private LocalDateTime deadline;
}

