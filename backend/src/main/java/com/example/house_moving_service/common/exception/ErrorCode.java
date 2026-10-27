package com.example.house_moving_service.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    ERR_INVALID_INPUT(1001, HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ: %s"),
    ERR_UNAUTHORIZED(1002, HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác!"),
    ERR_FORBIDDEN(1006, HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện hành động này"),
    ERR_CUSTOMER_NOT_FOUND(1007, HttpStatus.NOT_FOUND, "Không tìm thấy thông tin khách hàng cho tài khoản này"),
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
    ERR_BOOKING_NOT_FOUND(4001, HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng với ID: %s"),
    ERR_BOOKING_INVALID_STATUS(4002, HttpStatus.BAD_REQUEST, "Trạng thái đơn hàng hiện tại không cho phép thực hiện thao tác này"),
    ERR_VOUCHER_LIMIT_REACHED(4003, HttpStatus.BAD_REQUEST, "Voucher đã đạt giới hạn sử dụng tối đa"),
    ERR_BOOKING_FORBIDDEN(4004, HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập hoặc thao tác trên đơn hàng này"),
    ERR_SURVEY_NOT_FOUND(5001, HttpStatus.NOT_FOUND, "Không tìm thấy lịch khảo sát với ID: %s"),
    ERR_SURVEY_INVALID_STATUS(5002, HttpStatus.BAD_REQUEST, "Trạng thái khảo sát hiện tại không cho phép thực hiện thao tác này"),
    ERR_SURVEY_ACTIVE_EXISTS(5003, HttpStatus.CONFLICT, "Đơn hàng đã có lịch khảo sát đang hoạt động"),
    ERR_SURVEY_BOOKING_INVALID_STATUS(5004, HttpStatus.BAD_REQUEST, "Trạng thái đơn hàng không cho phép tạo hoặc thay đổi khảo sát"),
    ERR_SURVEY_STAFF_INVALID(5005, HttpStatus.BAD_REQUEST, "Nhân viên khảo sát không hợp lệ hoặc không còn hoạt động: %s"),
    ERR_SURVEY_STAFF_CONFLICT(5006, HttpStatus.CONFLICT, "Nhân viên đã có lịch khảo sát khác trong khung giờ này"),
    ERR_SURVEY_TIME_SLOT_NOT_FOUND(5007, HttpStatus.NOT_FOUND, "Không tìm thấy khung giờ khảo sát với ID: %s"),
    ERR_SURVEY_TIME_SLOT_INACTIVE(5008, HttpStatus.BAD_REQUEST, "Khung giờ khảo sát đã ngừng sử dụng"),
    ERR_SURVEY_TIME_SLOT_OVERLAP(5009, HttpStatus.CONFLICT, "Khung giờ bị trùng hoặc chồng lấn với khung giờ đang hoạt động"),
    ERR_SURVEY_INVALID_DATE(5010, HttpStatus.BAD_REQUEST, "Ngày/giờ khảo sát không hợp lệ: %s"),
    ERR_SURVEY_FORBIDDEN(5011, HttpStatus.FORBIDDEN, "Bạn không được phân công cho lịch khảo sát này"),
    ERR_SURVEY_RESULT_INCOMPLETE(5012, HttpStatus.BAD_REQUEST, "Chưa đủ dữ liệu để hoàn tất khảo sát: %s"),
    ERR_SURVEY_ATTACHMENT_NOT_FOUND(5013, HttpStatus.NOT_FOUND, "Không tìm thấy tệp đính kèm với ID: %s"),
    ERR_FILE_INVALID_TYPE(5014, HttpStatus.BAD_REQUEST, "Định dạng tệp không được hỗ trợ: %s"),
    ERR_FILE_TOO_LARGE(5015, HttpStatus.CONTENT_TOO_LARGE, "Tệp vượt quá dung lượng cho phép"),
    ERR_FILE_STORAGE(5016, HttpStatus.BAD_GATEWAY, "Không thể lưu trữ tệp, vui lòng thử lại"),
    ERR_SURVEY_ATTACHMENT_LIMIT(5017, HttpStatus.BAD_REQUEST, "Mỗi lịch khảo sát chỉ được tối đa %s tệp đính kèm"),
    ERR_SURVEY_TIME_SLOT_IN_USE(5019, HttpStatus.CONFLICT, "Khung giờ đang được dùng cho lịch khảo sát sắp tới, không thể đổi giờ"),
    ERR_QUOTATION_NOT_FOUND(6001, HttpStatus.NOT_FOUND, "Không tìm thấy báo giá với ID: %s"),
    ERR_QUOTATION_NOT_EDITABLE(6002, HttpStatus.BAD_REQUEST, "Báo giá không còn ở trạng thái nháp, không thể chỉnh sửa"),
    ERR_QUOTATION_ITEM_NOT_FOUND(6003, HttpStatus.NOT_FOUND, "Không tìm thấy dòng báo giá với ID: %s"),
    ERR_QUOTATION_ALREADY_ACCEPTED(6004, HttpStatus.CONFLICT, "Đơn hàng đã có báo giá được chấp nhận, không thể khảo sát lại"),
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
