package com.czl.teamupbackend.mq;

import com.czl.teamupbackend.config.TaskReminderRabbitConfig;
import com.czl.teamupbackend.model.mq.TaskDeadlineReminderMessage;
import com.czl.teamupbackend.service.TaskReminderMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDeadlineReminderConsumer {

    private final TaskReminderMailService taskReminderMailService;

    @RabbitListener(queues = TaskReminderRabbitConfig.QUEUE)
    public void consume(TaskDeadlineReminderMessage message) {
        int maxRetry = 3;
        long retrySleepMs = 1500L;
        for (int i = 1; i <= maxRetry; i++) {
            try {
                taskReminderMailService.sendTaskDeadlineReminder(message);
                return;
            } catch (Exception e) {
                log.warn("Task reminder mail send failed, attempt={}/{}, taskId={}, userId={}, error={}",
                    i, maxRetry, message.getTaskId(), message.getAssigneeUserId(), e.getMessage());
                if (i == maxRetry) {
                    log.error("Task reminder mail send failed after retries, taskId={}, userId={}",
                        message.getTaskId(), message.getAssigneeUserId(), e);
                    return;
                }
                try {
                    Thread.sleep(retrySleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}

