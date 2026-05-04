package com.czl.teamupbackend.schedule;

import com.czl.teamupbackend.config.TaskReminderRabbitConfig;
import com.czl.teamupbackend.mapper.TaskAssignmentMapper;
import com.czl.teamupbackend.model.mq.TaskDeadlineReminderMessage;
import com.czl.teamupbackend.model.vo.TaskDeadlineReminderCandidateVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDeadlineReminderScheduler {

    private final TaskAssignmentMapper taskAssignmentMapper;
    private final RabbitTemplate rabbitTemplate;
    @Value("${task-reminder.scan-on-startup:false}")
    private boolean scanOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void scanOnApplicationReady() {
        if (scanOnStartup) {
            log.info("Task deadline reminder startup scan enabled, executing once on startup");
            scanAndDispatchDeadlineReminder();
        }
    }

    @Scheduled(cron = "0 */10 * * * ?")
    public void scanAndDispatchDeadlineReminder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDeadline = now.plusHours(6);
        LocalDateTime endDeadline = startDeadline.plusMinutes(10);
        List<TaskDeadlineReminderCandidateVO> candidates =
            taskAssignmentMapper.selectDeadlineReminderCandidates(startDeadline, endDeadline);
        int candidateCount = candidates == null ? 0 : candidates.size();
        log.info("Task deadline reminder scan executed, now={}, windowStart={}, windowEnd={}, candidateCount={}",
            now, startDeadline, endDeadline, candidateCount);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (TaskDeadlineReminderCandidateVO candidate : candidates) {
            TaskDeadlineReminderMessage message = new TaskDeadlineReminderMessage();
            message.setTaskId(candidate.getTaskId());
            message.setAssigneeUserId(candidate.getAssigneeUserId());
            message.setAssigneeEmail(candidate.getAssigneeEmail());
            message.setAssigneeName(candidate.getAssigneeName());
            message.setTaskDescription(candidate.getTaskDescription());
            message.setDeadline(candidate.getDeadline());
            rabbitTemplate.convertAndSend(
                TaskReminderRabbitConfig.EXCHANGE,
                TaskReminderRabbitConfig.ROUTING_KEY,
                message
            );
        }
        log.info("Task deadline reminder dispatched, candidateCount={}", candidates.size());
    }
}
