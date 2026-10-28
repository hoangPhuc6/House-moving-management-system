package com.example.house_moving_service.common.exception;

import com.example.house_moving_service.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Tên unique index trong migration -> lỗi nghiệp vụ trả cho client (đổi tên index thì phải sửa ở đây)
    private static final Map<String, ErrorCode> CONSTRAINT_ERROR_CODES = Map.of(
            "uq_booking_surveys_staff_slot", ErrorCode.ERR_SURVEY_STAFF_CONFLICT,
            "uq_booking_surveys_one_active_per_booking", ErrorCode.ERR_SURVEY_ACTIVE_EXISTS
    );

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        ApiResponse<Void> body = ApiResponse.error(errorCode.getCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.ERR_INVALID_INPUT.getCode(),
                "Dữ liệu không hợp lệ",
                errors,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.ERR_UNAUTHORIZED.getCode(),
                "Email hoặc mật khẩu không chính xác!",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.ERR_FORBIDDEN.getCode(),
                ErrorCode.ERR_FORBIDDEN.formatMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.ERR_UNAUTHORIZED.getCode(),
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String msg = String.format("Tham số '%s' có giá trị không hợp lệ: '%s'", ex.getName(), ex.getValue());
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.ERR_INVALID_INPUT.getCode(),
                msg,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.ERR_INVALID_INPUT.getCode(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Lớp chặn cuối khi 2 request chạy đua cùng vượt qua kiểm tra ở service: DB từ chối theo unique index,
    // trả lỗi nghiệp vụ tương ứng thay vì 500. Vi phạm ràng buộc khác vẫn xử lý như lỗi hệ thống
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        String detail = ex.getMostSpecificCause().getMessage();
        if (detail != null) {
            for (Map.Entry<String, ErrorCode> entry : CONSTRAINT_ERROR_CODES.entrySet()) {
                if (detail.contains(entry.getKey())) {
                    ErrorCode errorCode = entry.getValue();
                    ApiResponse<Void> body = ApiResponse.error(errorCode.getCode(), errorCode.formatMessage(), request.getRequestURI());
                    return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
                }
            }
        }
        return handleGeneralException(ex, request);
    }

    // File vượt spring.servlet.multipart.max-file-size: bị chặn trước khi vào controller
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.ERR_FILE_TOO_LARGE;
        ApiResponse<Void> body = ApiResponse.error(errorCode.getCode(), errorCode.formatMessage(), request.getRequestURI());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    // Request multipart thiếu part bắt buộc (vd. không có field "file")
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.ERR_INVALID_INPUT.getCode(),
                ErrorCode.ERR_INVALID_INPUT.formatMessage("thiếu phần '" + ex.getRequestPartName() + "' trong request"),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex, HttpServletRequest request) {
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.ERR_INTERNAL_SERVER.getCode(),
                ErrorCode.ERR_INTERNAL_SERVER.formatMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
