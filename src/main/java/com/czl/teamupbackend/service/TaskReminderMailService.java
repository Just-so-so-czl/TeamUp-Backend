package com.czl.teamupbackend.service;

import com.czl.teamupbackend.model.mq.TaskDeadlineReminderMessage;

public interface TaskReminderMailService {

    void sendTaskDeadlineReminder(TaskDeadlineReminderMessage message);
}

