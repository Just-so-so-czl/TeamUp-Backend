package com.czl.teamupbackend.event;

import com.czl.teamupbackend.config.DocumentTextExtractRabbitConfig;
import com.czl.teamupbackend.model.mq.DocumentTextExtractMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在资料文档元数据事务提交后投递解析任务，避免消费者读到未提交的数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTextExtractTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(DocumentResourceUploadedEvent event) {
        if (event == null || event.documentId() == null) {
            return;
        }
        DocumentTextExtractMessage message = new DocumentTextExtractMessage();
        message.setDocumentId(event.documentId());
        rabbitTemplate.convertAndSend(
            DocumentTextExtractRabbitConfig.EXCHANGE,
            DocumentTextExtractRabbitConfig.ROUTING_KEY,
            message
        );
        log.info("Document text extraction task published, documentId={}", event.documentId());
    }
}
