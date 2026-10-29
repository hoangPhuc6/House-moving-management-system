package com.example.house_moving_service.modules.quotation.service;

/**
 * Module quotation định nghĩa, module survey cài đặt (dependency inversion: quotation không import survey).
 * Quotation PHẢI gọi trước khi tính giá / gửi khách một báo giá: không lập báo giá chính thức
 * từ khảo sát chưa hoàn tất.
 * Bean cài đặt phải nhỏ, chỉ phụ thuộc repository của survey, để không tạo vòng bean.
 */
public interface SurveyCompletionChecker {

    /** true nếu lần khảo sát mới nhất của booking đã COMPLETED. */
    boolean isLatestSurveyCompleted(Integer bookingId);
}
