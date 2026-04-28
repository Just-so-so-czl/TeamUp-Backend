package com.czl.teamupbackend.commen.exception;

import com.czl.teamupbackend.commen.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("Business exception: {}", e.getMessage());
        if (e.getCode() != null && e.getCode() == 401) {
            return Result.fail(401, e.getMessage(), "UNAUTHORIZED");
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail(500, "服务器开小差了，请稍后再试");
    }
}
