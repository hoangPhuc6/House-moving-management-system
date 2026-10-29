package com.example.house_moving_service.common.storage;

/** Lỗi từ nhà cung cấp lưu trữ (mạng, xác thực, bucket...). Module gọi đổi thành AppException(ERR_FILE_STORAGE). */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
