package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.IncidentFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.RejectIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.ReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.ResolveIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.IncidentResponse;
import org.springframework.data.domain.Pageable;

/**
 * Sự cố phía điều phối (ADMIN, DISPATCHER): tìm kiếm, ghi nhận sự cố do khách báo, xử lý.
 * Các bước đổi trạng thái khóa dòng sự cố. Sự cố không làm đổi trạng thái đơn hay chuyến.
 */
public interface ManagementIncidentService {

    // Không truyền sort thì mới nhất trước
    PageResponse<IncidentResponse> search(IncidentFilterRequest filter, Pageable pageable);

    IncidentResponse getDetail(Integer id);

    /**
     * Ghi nhận sự cố do khách báo. Đơn phải tồn tại (ERR_BOOKING_NOT_FOUND) và không CANCELLED
     * (ERR_INCIDENT_BOOKING_INVALID_STATUS); scheduleId nếu có phải thuộc đơn (ERR_INCIDENT_SCHEDULE_INVALID).
     */
    IncidentResponse record(Integer bookingId, ReportIncidentRequest request, Long userId);

    // REPORTED → INVESTIGATING; note (nếu có) lưu tạm ở resolution_note, sẽ bị thay bằng kết quả khi đóng sự cố
    IncidentResponse investigate(Integer id, MovingNoteRequest request, Long userId);

    // REPORTED / INVESTIGATING → RESOLVED, ghi chi phí thiệt hại, resolved_at, resolved_by
    IncidentResponse resolve(Integer id, ResolveIncidentRequest request, Long userId);

    // REPORTED / INVESTIGATING → REJECTED, ghi resolved_at, resolved_by
    IncidentResponse reject(Integer id, RejectIncidentRequest request, Long userId);
}
