package com.example.house_moving_service.modules.scheduling.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SendSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SurchargeFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SurchargeReviewNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.BookingSurchargeSummaryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SurchargeResponse;
import com.example.house_moving_service.modules.scheduling.service.ManagementSurchargeService;
import com.example.house_moving_service.security.access.StaffOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management")
@StaffOnly
@RequiredArgsConstructor
@Tag(name = "Management - Surcharges", description = "Điều phối viên chốt giá, gửi khách, bác hoặc rút báo giá điều chỉnh")
@SecurityRequirement(name = "bearerAuth")
public class ManagementSurchargeController {

    private final ManagementSurchargeService managementSurchargeService;

    @GetMapping("/surcharges")
    @Operation(
            summary = "Danh sách phụ phí cần xử lý",
            description = "Lọc theo status (mặc định PROPOSED), surchargeType, bookingId, khoảng ngày đề xuất from / to "
                    + "(yyyy-MM-dd). Không truyền sort thì cũ nhất trước. Lọc REJECTED_BY_CUSTOMER để biết đơn nào cần "
                    + "thương lượng lại hoặc hủy."
    )
    public ResponseEntity<ApiResponse<PageResponse<SurchargeResponse>>> search(
            SurchargeFilterRequest filter,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(managementSurchargeService.search(filter, pageable)));
    }

    @GetMapping("/bookings/{bookingId}/surcharges")
    @Operation(
            summary = "Toàn bộ phụ phí của đơn",
            description = "Mọi trạng thái, cũ nhất trước; kèm tổng tiền theo từng trạng thái, acceptedTotal, uncollectedTotal "
                    + "và id các khoản đang chặn bắt đầu chuyến."
    )
    public ResponseEntity<ApiResponse<BookingSurchargeSummaryResponse>> getByBooking(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId) {
        return ResponseEntity.ok(ApiResponse.ok(managementSurchargeService.getByBooking(bookingId)));
    }

    @PostMapping("/bookings/{bookingId}/surcharges")
    @Operation(
            summary = "Tự tạo báo giá điều chỉnh",
            description = "Tạo và gửi ngay cho khách → WAITING_CUSTOMER. Đơn không CANCELLED / COMPLETED. scheduleId không "
                    + "bắt buộc; nếu có thì chuyến phải thuộc đơn và ở CONFIRMED / IN_PROGRESS. Quy tắc đơn giá giống API "
                    + "đề xuất của hiện trường."
    )
    public ResponseEntity<ApiResponse<SurchargeResponse>> create(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @Valid @RequestBody CreateSurchargeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SurchargeResponse response = managementSurchargeService.create(bookingId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã tạo và gửi báo giá điều chỉnh cho khách", response));
    }

    @PatchMapping("/surcharges/{id}/send")
    @Operation(
            summary = "Chốt đề xuất và gửi khách",
            description = "PROPOSED → WAITING_CUSTOMER. Sửa được quantity; unitPrice chỉ sửa được với WAITING_TIME / OTHER. "
                    + "Không gửi field nào thì giữ nguyên đề xuất."
    )
    public ResponseEntity<ApiResponse<SurchargeResponse>> send(
            @Parameter(description = "ID phụ phí", required = true) @PathVariable Integer id,
            @Valid @RequestBody(required = false) SendSurchargeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SurchargeResponse response = managementSurchargeService.send(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã gửi báo giá điều chỉnh cho khách", response));
    }

    @PatchMapping("/surcharges/{id}/reject")
    @Operation(summary = "Bác đề xuất của trưởng nhóm", description = "PROPOSED → REJECTED. Bắt buộc reviewNote.")
    public ResponseEntity<ApiResponse<SurchargeResponse>> reject(
            @Parameter(description = "ID phụ phí", required = true) @PathVariable Integer id,
            @Valid @RequestBody SurchargeReviewNoteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SurchargeResponse response = managementSurchargeService.reject(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã bác đề xuất phụ phí", response));
    }

    @PatchMapping("/surcharges/{id}/withdraw")
    @Operation(
            summary = "Rút báo giá điều chỉnh",
            description = "WAITING_CUSTOMER / REJECTED_BY_CUSTOMER → CANCELLED, để thương lượng lại bằng khoản mới. "
                    + "Bắt buộc reviewNote."
    )
    public ResponseEntity<ApiResponse<SurchargeResponse>> withdraw(
            @Parameter(description = "ID phụ phí", required = true) @PathVariable Integer id,
            @Valid @RequestBody SurchargeReviewNoteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SurchargeResponse response = managementSurchargeService.withdraw(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã rút báo giá điều chỉnh", response));
    }
}
