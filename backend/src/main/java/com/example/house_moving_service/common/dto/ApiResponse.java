package com.example.house_moving_service.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Builder.Default
    private Integer code = 1000;

    private String message;
    private T data;
    private Object errors;
    private String path;

    @Builder.Default
    private Instant timestamp = Instant.now();

    // 1. Trả về thành công kèm dữ liệu
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(1000)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return success(data);
    }

    // 2. Trả về thành công kèm thông điệp tùy biến
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(1000)
                .message(message)
                .data(data)
                .build();
    }

    // 3. Trả về thành công không cần dữ liệu (Delete, Update trạng thái...)
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .code(1000)
                .message(message)
                .build();
    }

    // 4. Trả về lỗi nghiệp vụ (AppException)
    public static <T> ApiResponse<T> error(int code, String message, String path) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .path(path)
                .build();
    }

    // 5. Trả về lỗi kèm danh sách validation chi tiết
    public static <T> ApiResponse<T> error(int code, String message, Object errors, String path) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .errors(errors)
                .path(path)
                .build();
    }
}
