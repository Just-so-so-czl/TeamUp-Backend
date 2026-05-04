package com.czl.teamupbackend.model.mq;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TaskDeadlineReminderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long taskId;

    private Long assigneeUserId;

    private String assigneeEmail;

    private String assigneeName;

    private String taskDescription;

    private LocalDateTime deadline;
}

