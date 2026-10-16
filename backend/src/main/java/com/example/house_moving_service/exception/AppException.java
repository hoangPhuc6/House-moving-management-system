package com.example.house_moving_service.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String formattedMessage;

    public AppException(ErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
        this.formattedMessage = errorCode.formatMessage(args);
    }
}

