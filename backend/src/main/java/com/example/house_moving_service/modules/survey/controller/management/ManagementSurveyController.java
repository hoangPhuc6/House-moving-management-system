package com.example.house_moving_service.modules.survey.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.SurveyFilterRequest;
import com.example.house_moving_service.modules.survey.dto.response.StaffAvailabilityResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.service.SurveyManagementService;
import com.example.house_moving_service.modules.survey.service.SurveyQueryService;
import com.example.house_moving_service.security.access.StaffOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/management")
@Slf4j
@StaffOnly
@RequiredArgsConstructor
@Tag(name = "Management - Surveys", description = "Điều phối lịch khảo sát dành cho Quản trị viên & Điều phối viên")
@SecurityRequirement(name = "bearerAuth")
public class ManagementSurveyController {

    private final SurveyQueryService surveyQueryService;
    private final SurveyManagementService surveyManagementService;

    @PostMapping("/surveys")
    @Operation(
            summary = "Tạo lịch khảo sát cho đơn hàng",
            description = "Đơn phải ở PENDING / PENDING_SURVEY và chưa có lịch khảo sát đang hoạt động. "
                    + "Ngày, khung giờ, nhân viên có thể để trống và xếp sau; đủ cả ba thì lịch là CONFIRMED, thiếu thì PENDING."
    )
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> createSurvey(
            @Valid @RequestBody CreateSurveyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Management request to create survey for booking id: {} by user id: {}", request.getBookingId(), userDetails.getId());
        SurveyDetailResponse response = surveyManagementService.create(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo lịch khảo sát thành công", response));
    }

    @GetMapping("/surveys/movers")
    @Operation(summary = "Danh sách nhân viên khảo sát (MOVER) đang làm việc", description = "Sắp theo họ tên.")
    public ResponseEntity<ApiResponse<List<StaffSummary>>> listMovers() {
        return ResponseEntity.ok(ApiResponse.ok(surveyQueryService.listAvailableMovers()));
    }

    @GetMapping("/surveys/movers/{staffId}/availability")
    @Operation(
            summary = "Lịch rảnh / bận của một MOVER trong ngày",
            description = "Mỗi khung giờ đang bật một phần tử: available = false kèm lý do SURVEY (đã có lịch khảo sát) "
                    + "hoặc OTHER_SCHEDULE (bận việc khác). Không truyền date thì lấy hôm nay. Chỉ để hiển thị, "
                    + "chặn trùng thật sự vẫn kiểm tra lúc lưu."
    )
    public ResponseEntity<ApiResponse<StaffAvailabilityResponse>> getStaffAvailability(
            @Parameter(description = "ID nhân viên (user id)", required = true) @PathVariable Long staffId,
            @Parameter(description = "Ngày cần xem (yyyy-MM-dd), mặc định hôm nay")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(surveyQueryService.getStaffAvailability(staffId, date)));
    }

    @GetMapping("/surveys")
    @Operation(
            summary = "Tìm kiếm danh sách lịch khảo sát",
            description = "Lọc theo khoảng ngày, nhân viên, trạng thái, phương thức, mã đơn, số điện thoại liên hệ. "
                    + "Không truyền sort thì sắp theo ngày khảo sát rồi giờ bắt đầu, lịch chưa có ngày xếp cuối."
    )
    public ResponseEntity<ApiResponse<PageResponse<SurveySummaryResponse>>> searchSurveys(
            SurveyFilterRequest filter,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(surveyQueryService.search(filter, pageable)));
    }

    @GetMapping("/surveys/{id}")
    @Operation(
            summary = "Xem chi tiết một lịch khảo sát",
            description = "Kèm tuyến đường (điều kiện ra vào), bản nháp báo giá hiện tại nếu là lần khảo sát mới nhất "
                    + "và các lần khảo sát khác của cùng đơn hàng."
    )
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> getSurveyDetail(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(surveyQueryService.getDetail(id)));
    }

    @GetMapping("/bookings/{bookingId}/surveys")
    @Operation(summary = "Mọi lần khảo sát của một đơn hàng", description = "Sắp mới nhất trước.")
    public ResponseEntity<ApiResponse<List<SurveySummaryResponse>>> getSurveysByBooking(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId) {
        return ResponseEntity.ok(ApiResponse.ok(surveyQueryService.getByBooking(bookingId)));
    }
}
