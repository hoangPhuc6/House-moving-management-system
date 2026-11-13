package com.example.house_moving_service.modules.scheduling.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.IncidentFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.RejectIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.ReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.ResolveIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.IncidentResponse;
import com.example.house_moving_service.modules.scheduling.service.ManagementIncidentService;
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
@Tag(name = "Management - Incidents", description = "Điều phối viên ghi nhận và xử lý sự cố vận chuyển")
@SecurityRequirement(name = "bearerAuth")
public class ManagementIncidentController {

    private final ManagementIncidentService managementIncidentService;

    @GetMapping("/incidents")
    @Operation(
            summary = "Tìm kiếm sự cố",
            description = "Lọc theo status, incidentType, bookingId, scheduleId, khoảng ngày báo from / to (yyyy-MM-dd). "
                    + "Không truyền sort thì mới nhất trước."
    )
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> search(
            IncidentFilterRequest filter,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(managementIncidentService.search(filter, pageable)));
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Chi tiết sự cố")
    public ResponseEntity<ApiResponse<IncidentResponse>> getDetail(
            @Parameter(description = "ID sự cố", required = true) @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(managementIncidentService.getDetail(id)));
    }

    @PostMapping("/bookings/{bookingId}/incidents")
    @Operation(
            summary = "Ghi nhận sự cố do khách báo",
            description = "Đơn phải tồn tại và không CANCELLED. scheduleId không bắt buộc; nếu có thì chuyến phải thuộc đơn."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> record(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @Valid @RequestBody ReportIncidentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentResponse response = managementIncidentService.record(bookingId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã ghi nhận sự cố", response));
    }

    @PatchMapping("/incidents/{id}/investigate")
    @Operation(
            summary = "Bắt đầu điều tra sự cố",
            description = "REPORTED → INVESTIGATING. note không bắt buộc, lưu tạm làm ghi chú xử lý (khách không thấy)."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> investigate(
            @Parameter(description = "ID sự cố", required = true) @PathVariable Integer id,
            @Valid @RequestBody(required = false) MovingNoteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentResponse response = managementIncidentService.investigate(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã chuyển sự cố sang điều tra", response));
    }

    @PatchMapping("/incidents/{id}/resolve")
    @Operation(
            summary = "Giải quyết sự cố",
            description = "REPORTED / INVESTIGATING → RESOLVED. Bắt buộc resolutionNote và damageCost (chi phí công ty chịu, "
                    + "không cộng vào tiền của khách)."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> resolve(
            @Parameter(description = "ID sự cố", required = true) @PathVariable Integer id,
            @Valid @RequestBody ResolveIncidentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentResponse response = managementIncidentService.resolve(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã giải quyết sự cố", response));
    }

    @PatchMapping("/incidents/{id}/reject")
    @Operation(summary = "Bác bỏ sự cố", description = "REPORTED / INVESTIGATING → REJECTED. Bắt buộc resolutionNote.")
    public ResponseEntity<ApiResponse<IncidentResponse>> reject(
            @Parameter(description = "ID sự cố", required = true) @PathVariable Integer id,
            @Valid @RequestBody RejectIncidentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentResponse response = managementIncidentService.reject(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã bác bỏ sự cố", response));
    }
}
