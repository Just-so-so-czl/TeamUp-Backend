package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.model.mq.TaskDeadlineReminderMessage;
import com.czl.teamupbackend.service.TaskReminderMailService;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskReminderMailServiceImpl implements TaskReminderMailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Override
    public void sendTaskDeadlineReminder(TaskDeadlineReminderMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(mailFrom);
        mail.setTo(message.getAssigneeEmail());
        mail.setSubject("【TeamUp 任务提醒】任务即将在 6 小时后到期");
        String deadlineText = message.getDeadline() == null
            ? "未知"
            : message.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String content = "你好，" + safe(message.getAssigneeName()) + "：\n\n"
            + "你认领的任务尚未完成，距离截止时间还有 6 小时。\n"
            + "任务内容：" + safe(message.getTaskDescription()) + "\n"
            + "截止时间：" + deadlineText + "\n\n"
            + "请尽快处理，避免逾期。\n"
            + "—— TeamUp";
        mail.setText(content);
        mailSender.send(mail);
        log.info("Task deadline reminder mail sent, taskId={}, userId={}, email={}",
            message.getTaskId(), message.getAssigneeUserId(), message.getAssigneeEmail());
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}

