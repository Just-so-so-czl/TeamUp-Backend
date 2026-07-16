package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.service.TeamMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamMailServiceImpl implements TeamMailService {
    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String mailFrom;

    @Override
    public void sendPlainMail(String recipientEmail, String subject, String content) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(mailFrom);
        mail.setTo(recipientEmail);
        mail.setSubject(subject);
        mail.setText(content);
        mailSender.send(mail);
        log.info("Team mail sent, recipientEmail={}, subjectLength={}", recipientEmail, subject.length());
    }
}
