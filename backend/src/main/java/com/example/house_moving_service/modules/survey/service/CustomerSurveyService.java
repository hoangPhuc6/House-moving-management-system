package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.modules.survey.dto.response.CustomerSurveyResponse;

import java.util.List;

/** Khách hàng xem lịch khảo sát của đơn mình (chỉ xem; đổi lịch thì liên hệ điều phối viên). */
public interface CustomerSurveyService {

    /** Mọi lần khảo sát của đơn, mới nhất trước. Đơn không phải của người gọi → ERR_BOOKING_FORBIDDEN. */
    List<CustomerSurveyResponse> getByBooking(Integer bookingId, Long currentUserId);
}
