package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.survey.dto.request.CompleteSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.FailSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.RouteAccessRequest;
import com.example.house_moving_service.modules.survey.dto.request.SurveyItemRequest;
import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Luồng của MOVER được phân công đi khảo sát. Mọi thao tác chỉ áp dụng cho lịch của chính người gọi
 * (lịch người khác → ERR_SURVEY_FORBIDDEN).
 */
public interface FieldSurveyService {

    /**
     * Lịch khảo sát của tôi trong khoảng ngày, sắp theo ngày rồi giờ bắt đầu. from null → hôm nay, to null → from + 7 ngày;
     * status null → mọi trạng thái. Lịch chưa có ngày không hiện.
     */
    List<SurveySummaryResponse> getMySurveys(Long userId, LocalDate from, LocalDate to, SurveyStatus status);

    FieldSurveyResponse getMySurvey(Integer id, Long userId);

    /**
     * Bắt đầu khảo sát lịch CONFIRMED của mình, trong ngày khảo sát và không sớm hơn app.survey.early-start-minutes
     * trước giờ hẹn. Lịch → IN_PROGRESS + actualStartAt; booking → SURVEYING; mở bản nháp báo giá.
     */
    FieldSurveyResponse start(Integer id, Long userId);

    // Thêm / sửa / xóa đồ đạc trên bản nháp báo giá hiện tại; lịch phải IN_PROGRESS. Không đụng booking_items
    QuotationItemDto addItem(Integer id, SurveyItemRequest request, Long userId);

    QuotationItemDto updateItem(Integer id, Integer itemId, SurveyItemRequest request, Long userId);

    void deleteItem(Integer id, Integer itemId, Long userId);

    /**
     * Ghi tầng / thang máy / cự ly hẻm thực tế ở hai đầu (lịch phải IN_PROGRESS). Giá trị trong booking_routes bị ghi đè
     * nên giá trị cũ → mới được nối vào note của lịch (tag ROUTE_UPDATE). Không có gì thay đổi thì không ghi gì.
     */
    FieldSurveyResponse updateRouteAccess(Integer id, RouteAccessRequest request, Long userId);

    /**
     * Hoàn tất lịch IN_PROGRESS của mình: bản nháp phải có ít nhất 1 món đồ. Lịch → COMPLETED + actualEndAt;
     * resultNote (nếu có) nối vào note tag RESULT. Booking giữ SURVEYING. request null = không có nhận xét.
     */
    FieldSurveyResponse complete(Integer id, CompleteSurveyRequest request, Long userId);

    // Báo thất bại lịch của mình: kiểm tra sở hữu rồi dùng lại SurveyManagementService#fail
    FieldSurveyResponse fail(Integer id, FailSurveyRequest request, Long userId);
}
