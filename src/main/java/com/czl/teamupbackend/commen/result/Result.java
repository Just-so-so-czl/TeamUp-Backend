package com.czl.teamupbackend.commen.result;

import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 错误类型（成功时为空）
     */
    private String errorType;

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.errorType = null;
    }

    public Result(Integer code, String message, T data, String errorType) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.errorType = errorType;
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(Integer code, String message, String errorType) {
        return new Result<>(code, message, null, errorType);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}
