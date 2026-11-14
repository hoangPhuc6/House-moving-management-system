package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SendSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SurchargeFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SurchargeReviewNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.BookingSurchargeSummaryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SurchargeResponse;
import org.springframework.data.domain.Pageable;

/** Phụ phí phía điều phối (ADMIN, DISPATCHER). */
public interface ManagementSurchargeService {

    // Danh sách khoản cần xử lý; không truyền status thì PROPOSED, không truyền sort thì cũ nhất trước
    PageResponse<SurchargeResponse> search(SurchargeFilterRequest filter, Pageable pageable);

    // Toàn bộ khoản của đơn kèm tổng tiền theo từng trạng thái; đơn không tồn tại → ERR_BOOKING_NOT_FOUND
    BookingSurchargeSummaryResponse getByBooking(Integer bookingId);

    /**
     * Điều phối viên tự tạo báo giá điều chỉnh và gửi ngay cho khách → WAITING_CUSTOMER. Nếu có scheduleId thì chuyến
     * phải thuộc đơn và ở CONFIRMED / IN_PROGRESS (ERR_SURCHARGE_SCHEDULE_INVALID).
     */
    SurchargeResponse create(Integer bookingId, CreateSurchargeRequest request, Long userId);

    // Chốt đề xuất rồi gửi khách: PROPOSED → WAITING_CUSTOMER; sửa được quantity, unitPrice chỉ với WAITING_TIME / OTHER
    SurchargeResponse send(Integer id, SendSurchargeRequest request, Long userId);

    // Bác đề xuất của trưởng nhóm: PROPOSED → REJECTED
    SurchargeResponse reject(Integer id, SurchargeReviewNoteRequest request, Long userId);

    // Rút khoản đã gửi / khách đã từ chối để thương lượng lại: WAITING_CUSTOMER / REJECTED_BY_CUSTOMER → CANCELLED
    SurchargeResponse withdraw(Integer id, SurchargeReviewNoteRequest request, Long userId);
}
