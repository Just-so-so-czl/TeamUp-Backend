package com.czl.teamupbackend.model.mq;

import java.io.Serializable;
import lombok.Data;

/**
 * 资料文档文本提取任务消息。
 */
@Data
public class DocumentTextExtractMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long documentId;
}
