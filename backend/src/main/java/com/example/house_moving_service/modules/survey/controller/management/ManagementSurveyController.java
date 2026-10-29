package com.example.house_moving_service.modules.survey.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.survey.dto.request.AssignSurveyStaffRequest;
import com.example.house_moving_service.modules.survey.dto.request.CancelSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.FailSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.RescheduleSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.ResurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.SurveyFilterRequest;
import com.example.house_moving_service.modules.survey.dto.response.StaffAvailabilityResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyAttachmentResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.service.SurveyAttachmentService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final SurveyAttachmentService surveyAttachmentService;

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

    @PatchMapping("/surveys/{id}/assign")
    @Operation(
            summary = "Phân công / đổi nhân viên khảo sát",
            description = "Lịch phải ở PENDING / CONFIRMED. Ngày và khung giờ giữ nguyên; lịch đã có ngày + khung giờ thì "
                    + "kiểm tra người mới không trùng lịch. Lịch có đủ ngày, khung giờ, nhân viên sẽ thành CONFIRMED."
    )
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> assignStaff(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Valid @RequestBody AssignSurveyStaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Management request to assign staff {} to survey id: {} by user id: {}", request.getStaffId(), id, userDetails.getId());
        SurveyDetailResponse response = surveyManagementService.assign(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Phân công nhân viên khảo sát thành công", response));
    }

    @PatchMapping("/surveys/{id}/reschedule")
    @Operation(
            summary = "Dời lịch hẹn khảo sát (trước khi khảo sát)",
            description = "Dùng khi khách xin đổi ngày / giờ. Lịch phải ở PENDING / CONFIRMED; đã bắt đầu khảo sát thì không dời được. "
                    + "staffId / surveyMethod để trống thì giữ như cũ. Bắt buộc có lý do, được ghi vào ghi chú kèm lịch cũ → lịch mới."
    )
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> rescheduleSurvey(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Valid @RequestBody RescheduleSurveyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Management request to reschedule survey id: {} by user id: {}", id, userDetails.getId());
        SurveyDetailResponse response = surveyManagementService.reschedule(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đổi lịch khảo sát thành công", response));
    }

    @PatchMapping("/surveys/{id}/cancel")
    @Operation(
            summary = "Hủy lịch khảo sát chưa diễn ra",
            description = "Lịch phải ở PENDING / CONFIRMED (lịch không cần nữa). Đã tới nơi / đang khảo sát mà không làm được "
                    + "thì dùng API báo thất bại. Không đổi trạng thái đơn hàng; tạo lịch mới sau nếu cần."
    )
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> cancelSurvey(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Valid @RequestBody CancelSurveyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Management request to cancel survey id: {} by user id: {}", id, userDetails.getId());
        SurveyDetailResponse response = surveyManagementService.cancel(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Hủy lịch khảo sát thành công", response));
    }

    @PatchMapping("/surveys/{id}/fail")
    @Operation(
            summary = "Báo khảo sát thất bại",
            description = "Đã tới nơi / đang khảo sát nhưng không làm được (khách vắng, không vào được nhà...). Lịch phải ở "
                    + "CONFIRMED / IN_PROGRESS → RESURVEY_REQUIRED; bản nháp báo giá hết hiệu lực; đơn đang SURVEYING về "
                    + "PENDING_SURVEY để tạo lịch khảo sát mới."
    )
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> failSurvey(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Valid @RequestBody FailSurveyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Management request to mark survey id: {} as failed by user id: {}", id, userDetails.getId());
        SurveyDetailResponse response = surveyManagementService.fail(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận khảo sát thất bại", response));
    }

    @PostMapping("/surveys/{id}/resurvey")
    @Operation(
            summary = "Khảo sát lại (sau khi đã khảo sát xong)",
            description = "Lịch phải COMPLETED và là lần khảo sát mới nhất; đơn ở SURVEYING / QUOTED và khách chưa chấp nhận báo giá. "
                    + "Lịch cũ → RESURVEY_REQUIRED, báo giá đang mở hết hiệu lực, đơn về PENDING_SURVEY, tạo lịch khảo sát mới "
                    + "(có thể truyền luôn ngày / khung giờ / nhân viên). Trả về lịch MỚI."
    )
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> resurvey(
            @Parameter(description = "ID lịch khảo sát đã hoàn tất", required = true) @PathVariable Integer id,
            @Valid @RequestBody ResurveyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Management request to resurvey from survey id: {} by user id: {}", id, userDetails.getId());
        SurveyDetailResponse response = surveyManagementService.resurvey(id, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo lịch khảo sát lại thành công", response));
    }

    @PostMapping(value = "/surveys/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Tải lên tệp đính kèm cho lịch khảo sát",
            description = "Lịch chưa bị hủy (kể cả đã hoàn tất, để bổ sung ảnh khách gửi, khảo sát qua điện thoại / online). "
                    + "Ảnh ≤ 10MB, video ≤ 50MB, PDF ≤ 10MB; tối đa 50 tệp mỗi lịch, trong đó tối đa 5 video. Field multipart: file."
    )
    public ResponseEntity<ApiResponse<SurveyAttachmentResponse>> uploadAttachment(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Management request to upload attachment to survey id: {} by user id: {}", id, userDetails.getId());
        SurveyAttachmentResponse response = surveyAttachmentService.upload(id, file, userDetails.getId(), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã tải lên tệp đính kèm", response));
    }

    @GetMapping("/surveys/{id}/attachments")
    @Operation(summary = "Danh sách tệp đính kèm của lịch khảo sát", description = "accessUrl có hạn 15 phút, gọi lại để lấy URL mới.")
    public ResponseEntity<ApiResponse<List<SurveyAttachmentResponse>>> listAttachments(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(surveyAttachmentService.list(id, userDetails.getId(), true)));
    }

    @DeleteMapping("/surveys/{id}/attachments/{attachmentId}")
    @Operation(summary = "Xóa tệp đính kèm", description = "Mọi tệp của lịch, khi lịch chưa từng hoàn tất.")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Parameter(description = "ID tệp đính kèm", required = true) @PathVariable Integer attachmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Management request to delete attachment id: {} of survey id: {} by user id: {}", attachmentId, id, userDetails.getId());
        surveyAttachmentService.delete(id, attachmentId, userDetails.getId(), true);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa tệp đính kèm"));
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
