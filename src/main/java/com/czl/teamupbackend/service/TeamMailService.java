package com.czl.teamupbackend.service;

public interface TeamMailService {
    void sendPlainMail(String recipientEmail, String subject, String content);
}
