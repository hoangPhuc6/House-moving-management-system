package com.example.house_moving_service.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    ERR_INVALID_INPUT(1001, HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ: %s"),
    ERR_UNAUTHORIZED(1002, HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác!"),
    ERR_FORBIDDEN(1006, HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện hành động này"),
    ERR_SERVICE_TYPE_NOT_FOUND(2001, HttpStatus.NOT_FOUND, "Không tìm thấy loại dịch vụ với ID: %s"),
    ERR_SERVICE_NOT_FOUND(2002, HttpStatus.NOT_FOUND, "Không tìm thấy dịch vụ với ID: %s"),
    ERR_SERVICE_TYPE_INACTIVE(2003, HttpStatus.BAD_REQUEST, "Không thể tạo hoặc kích hoạt dịch vụ thuộc nhóm loại dịch vụ đang bị vô hiệu hóa"),
    ERR_SERVICE_INACTIVE(2004, HttpStatus.BAD_REQUEST, "Dịch vụ đã ngừng hoạt động: %s"),
    ERR_DUPLICATE_NAME(2005, HttpStatus.CONFLICT, "Tên đã tồn tại trên hệ thống: %s"),
    ERR_PRICE_INVALID_INPUT(3001, HttpStatus.BAD_REQUEST, "Dữ liệu tính giá không hợp lệ: %s"),
    ERR_INVALID_SCHEDULE(3002, HttpStatus.BAD_REQUEST, "Thời gian bắt đầu vận chuyển phải cách hiện tại tối thiểu %d giờ"),
    ERR_VOUCHER_NOT_FOUND(3003, HttpStatus.NOT_FOUND, "Mã voucher '%s' không tồn tại trên hệ thống"),
    ERR_VOUCHER_EXPIRED(3004, HttpStatus.BAD_REQUEST, "Voucher '%s' đã hết hạn hoặc chưa tới thời gian áp dụng"),
    ERR_VOUCHER_MIN_VALUE(3005, HttpStatus.BAD_REQUEST, "Đơn hàng chưa đạt giá trị tối thiểu (%,.0f đ) để áp dụng voucher"),
    ERR_INTERNAL_SERVER(9999, HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống ngoài dự tính");

    private final int code;
    private final HttpStatus httpStatus;
    private final String messageTemplate;

    ErrorCode(int code, HttpStatus httpStatus, String messageTemplate) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageTemplate = messageTemplate;
    }

    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate.replace(": %s", "").replace(" %s", "");
        }
        return String.format(messageTemplate, args);
    }
}
