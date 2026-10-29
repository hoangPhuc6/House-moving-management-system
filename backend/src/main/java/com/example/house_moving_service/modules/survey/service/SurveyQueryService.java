package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.survey.dto.request.SurveyFilterRequest;
import com.example.house_moving_service.modules.survey.dto.response.StaffAvailabilityResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Các thao tác chỉ đọc trên lịch khảo sát. Thao tác ghi nằm ở SurveyManagementService;
 * tách riêng để luồng MOVER (task 4.7) và khách hàng (task 4.9) dùng lại phần đọc mà không kéo theo phần ghi.
 */
public interface SurveyQueryService {

    // Không truyền sort: sắp theo ngày khảo sát rồi giờ bắt đầu, lịch chưa có ngày xếp cuối
    PageResponse<SurveySummaryResponse> search(SurveyFilterRequest filter, Pageable pageable);

    SurveyDetailResponse getDetail(Integer id);

    // Mọi lần khảo sát của booking, mới nhất trước
    List<SurveySummaryResponse> getByBooking(Integer bookingId);

    // MOVER đang làm việc, sắp theo họ tên: danh sách để chọn người khảo sát
    List<StaffSummary> listAvailableMovers();

    /**
     * Rảnh / bận của một MOVER trong một ngày, theo từng khung giờ đang bật. Bận nếu đã có lịch khảo sát
     * CONFIRMED / IN_PROGRESS ở khung giờ đó, hoặc StaffExternalScheduleChecker (nếu có) báo bận việc khác.
     * Chỉ để hiển thị: chặn trùng thật sự vẫn do SurveyScheduleValidator làm lúc lưu.
     * date null → hôm nay (giờ Việt Nam).
     */
    StaffAvailabilityResponse getStaffAvailability(Long staffId, LocalDate date);
}
