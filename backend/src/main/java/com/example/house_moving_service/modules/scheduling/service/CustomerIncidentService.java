package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.modules.scheduling.dto.request.CustomerReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerIncidentResponse;

import java.util.List;

/** Khách xem và tự báo sự cố của đơn mình. */
public interface CustomerIncidentService {

    /**
     * Sự cố của đơn, mới nhất trước, bỏ sự cố REJECTED. Đơn không tồn tại → ERR_BOOKING_NOT_FOUND;
     * không phải chủ đơn → ERR_INCIDENT_FORBIDDEN.
     */
    List<CustomerIncidentResponse> getIncidents(Integer bookingId, Long customerUserId);

    /**
     * Khách tự báo sự cố. Luôn tạo ở trạng thái REPORTED, chi phí 0, để điều phối viên kiểm tra rồi mới giải quyết
     * hoặc bác bỏ. Chỉ chủ đơn (8103); đơn CANCELLED → 8105; đơn chưa vận chuyển (khác IN_PROGRESS / COMPLETED) hoặc
     * đã COMPLETED quá số ngày cấu hình → 8106; scheduleId không tồn tại, không thuộc đơn hoặc đã CANCELLED → 8104.
     */
    CustomerIncidentResponse report(Integer bookingId, CustomerReportIncidentRequest request, Long customerUserId);
}
