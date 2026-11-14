package com.example.house_moving_service.modules.scheduling.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.AssignStaffRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.AssignVehiclesRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.AvailabilityQuery;
import com.example.house_moving_service.modules.scheduling.dto.request.CancelMovingScheduleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateMovingScheduleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingScheduleFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.RescheduleMovingRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.ManagementMovingScheduleResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSummaryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingStaffAvailabilityResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import com.example.house_moving_service.modules.scheduling.service.ManagementMovingScheduleService;
import com.example.house_moving_service.modules.scheduling.service.MovingScheduleQueryService;
import com.example.house_moving_service.security.access.StaffOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management")
@StaffOnly
@RequiredArgsConstructor
@Tag(name = "Management - Moving schedules", description = "Điều phối lập lịch vận chuyển, phân công xe và nhân viên, đổi lịch, hủy chuyến")
@SecurityRequirement(name = "bearerAuth")
public class ManagementMovingScheduleController {

    private final ManagementMovingScheduleService movingScheduleService;
    private final MovingScheduleQueryService movingScheduleQueryService;

    @GetMapping("/moving-schedules")
    @Operation(
            summary = "Danh sách chuyến (phân trang)",
            description = "Lọc theo from / to (yyyy-MM-dd, chuyến giao với khoảng ngày), status, bookingId, vehicleId, staffId "
                    + "(phân công còn hiệu lực). Mặc định sắp theo giờ bắt đầu tăng dần; page bắt đầu từ 0."
    )
    public ResponseEntity<ApiResponse<PageResponse<MovingScheduleSummaryResponse>>> search(
            @ParameterObject MovingScheduleFilterRequest filter,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(movingScheduleQueryService.search(filter, pageable)));
    }

    @GetMapping("/moving-schedules/available-vehicles")
    @Operation(
            summary = "Xe rảnh trong khung giờ",
            description = "Bắt buộc start, end (ISO, vd 2026-11-20T08:00:00). Xe AVAILABLE / IN_USE không nằm trong chuyến chưa xong "
                    + "nào trùng giờ. excludeScheduleId: bỏ qua chuyến đang sửa; vehicleTypeId: lọc theo loại xe."
    )
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> availableVehicles(@ParameterObject AvailabilityQuery query) {
        return ResponseEntity.ok(ApiResponse.success(movingScheduleQueryService.findAvailableVehicles(query)));
    }

    @GetMapping("/moving-schedules/available-staff")
    @Operation(
            summary = "Nhân viên hiện trường và tình trạng rảnh trong khung giờ",
            description = "Bắt buộc start, end. Trả mọi MOVER đang làm việc, kèm available và busyReason "
                    + "(MOVING = trùng chuyến khác, SURVEY = trùng lịch khảo sát). excludeScheduleId: bỏ qua chuyến đang sửa."
    )
    public ResponseEntity<ApiResponse<List<MovingStaffAvailabilityResponse>>> availableStaff(@ParameterObject AvailabilityQuery query) {
        return ResponseEntity.ok(ApiResponse.success(movingScheduleQueryService.findStaffAvailability(query)));
    }

    @PostMapping("/bookings/{bookingId}/moving-schedules")
    @Operation(
            summary = "Tạo chuyến vận chuyển cho đơn",
            description = "Đơn phải CONFIRMED (đã đặt cọc) hoặc SCHEDULED (thêm chuyến). Giờ bắt đầu ở tương lai, kết thúc sau bắt đầu, "
                    + "tối đa app.moving.max-schedule-hours (mặc định 24h). Chuyến mới PLANNED, đơn chuyển SCHEDULED. "
                    + "Giờ dạng ISO, vd 2026-11-20T08:00:00."
    )
    public ResponseEntity<ApiResponse<ManagementMovingScheduleResponse>> create(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @Valid @RequestBody CreateMovingScheduleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ManagementMovingScheduleResponse response = movingScheduleService.create(bookingId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo lịch vận chuyển thành công", response));
    }

    @GetMapping("/moving-schedules/{id}")
    @Operation(summary = "Chi tiết chuyến", description = "Kèm mọi phân công xe / nhân viên (cả dòng đã hủy) và nhật ký.")
    public ResponseEntity<ApiResponse<ManagementMovingScheduleResponse>> getDetail(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(movingScheduleService.getDetail(id)));
    }

    @PutMapping("/moving-schedules/{id}/vehicles")
    @Operation(
            summary = "Phân công xe (ghi đè danh sách)",
            description = "Chuyến PLANNED / CONFIRMED. Xe không được MAINTENANCE / INACTIVE và không được nằm trong chuyến chưa xong "
                    + "khác trùng khung giờ (chạm biên không tính). Xe không có trong danh sách bị hủy phân công."
    )
    public ResponseEntity<ApiResponse<ManagementMovingScheduleResponse>> assignVehicles(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer id,
            @Valid @RequestBody AssignVehiclesRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Phân công xe thành công",
                movingScheduleService.assignVehicles(id, request, userDetails.getId())));
    }

    @PutMapping("/moving-schedules/{id}/staff")
    @Operation(
            summary = "Phân công nhân viên (ghi đè danh sách)",
            description = "Chuyến PLANNED / CONFIRMED. Đúng 1 SUPERVISOR; nhân viên là MOVER đang làm việc, không trùng chuyến chưa xong "
                    + "khác và không trùng lịch khảo sát CONFIRMED / IN_PROGRESS. Người không có trong danh sách bị hủy phân công."
    )
    public ResponseEntity<ApiResponse<ManagementMovingScheduleResponse>> assignStaff(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer id,
            @Valid @RequestBody AssignStaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Phân công nhân viên thành công",
                movingScheduleService.assignStaff(id, request, userDetails.getId())));
    }

    @PatchMapping("/moving-schedules/{id}/confirm")
    @Operation(
            summary = "Xác nhận chuyến",
            description = "PLANNED → CONFIRMED khi đã có ít nhất 1 xe và 1 giám sát viên. Giám sát viên chỉ bắt đầu được chuyến CONFIRMED."
    )
    public ResponseEntity<ApiResponse<ManagementMovingScheduleResponse>> confirm(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Xác nhận chuyến thành công",
                movingScheduleService.confirm(id, userDetails.getId())));
    }

    @PatchMapping("/moving-schedules/{id}/reschedule")
    @Operation(
            summary = "Đổi lịch chuyến",
            description = "Chuyến PLANNED / CONFIRMED. Giờ mới theo cùng quy tắc như khi tạo; xe và nhân viên đang phân công phải còn rảnh "
                    + "ở giờ mới. Giữ nguyên trạng thái chuyến, ghi nhật ký RESCHEDULE kèm lý do."
    )
    public ResponseEntity<ApiResponse<ManagementMovingScheduleResponse>> reschedule(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer id,
            @Valid @RequestBody RescheduleMovingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Đổi lịch chuyến thành công",
                movingScheduleService.reschedule(id, request, userDetails.getId())));
    }

    @PatchMapping("/moving-schedules/{id}/cancel")
    @Operation(
            summary = "Hủy chuyến",
            description = "Chuyến chưa bắt đầu (PLANNED / CONFIRMED / RESCHEDULED). Mọi phân công bị hủy. Đơn không còn chuyến chưa xong: "
                    + "SCHEDULED → CONFIRMED (chờ lập lịch lại), IN_PROGRESS → COMPLETED."
    )
    public ResponseEntity<ApiResponse<ManagementMovingScheduleResponse>> cancel(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer id,
            @Valid @RequestBody CancelMovingScheduleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Hủy chuyến thành công",
                movingScheduleService.cancel(id, request, userDetails.getId())));
    }
}
