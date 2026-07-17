package com.czl.teamupbackend.commen.exception;

/**
 * 草案预览阶段的可恢复参数错误，应返回给模型自行修正，而不是终止整轮对话。
 */
public class CollaborationDocumentPatchValidationException extends RuntimeException {

    public CollaborationDocumentPatchValidationException(String message) {
        super(message);
    }
}
