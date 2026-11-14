package com.example.house_moving_service.common.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String formattedMessage;
    // Dữ liệu bổ sung trả trong field "errors" của ApiResponse (ví dụ id các khoản đang chặn); thường null
    private final transient Object details;

    public AppException(ErrorCode errorCode, Object... args) {
        this(null, errorCode, args);
    }

    private AppException(Object details, ErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
        this.formattedMessage = errorCode.formatMessage(args);
        this.details = details;
    }

    public static AppException withDetails(ErrorCode errorCode, Object details, Object... args) {
        return new AppException(details, errorCode, args);
    }
}
