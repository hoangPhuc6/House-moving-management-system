package com.example.house_moving_service.modules.scheduling.controller.field;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingProgressRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.FieldMovingScheduleResponse;
import com.example.house_moving_service.modules.scheduling.service.FieldMovingScheduleService;
import com.example.house_moving_service.security.access.MoverOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/field/moving-schedules")
@MoverOnly
@RequiredArgsConstructor
@Tag(name = "Field - Moving schedules", description = "Tiến độ chuyến vận chuyển: nhân viên được phân công xem, giám sát viên cập nhật")
@SecurityRequirement(name = "bearerAuth")
public class FieldMovingScheduleController {

    private final FieldMovingScheduleService fieldMovingScheduleService;

    @GetMapping("/{id}")
    @Operation(
            summary = "Chi tiết chuyến vận chuyển",
            description = "Lịch hẹn, thời gian thực tế, độ trễ (phút), liên hệ, tuyến đường, nhân viên, xe và nhật ký. "
                    + "Mọi nhân viên có phân công chưa hủy trên chuyến đều xem được; không được phân công → 403."
    )
    public ResponseEntity<ApiResponse<FieldMovingScheduleResponse>> getDetail(
            @Parameter(description = "ID chuyến vận chuyển", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(fieldMovingScheduleService.getDetail(id, userDetails.getId())));
    }

    @PatchMapping("/{id}/start")
    @Operation(
            summary = "Bắt đầu chuyến",
            description = "Chỉ giám sát viên. Chuyến phải CONFIRMED; chỉ trong ngày của giờ hẹn, sớm nhất 60 phút (cấu hình) "
                    + "trước giờ hẹn, bắt đầu muộn không bị chặn. Chuyến → IN_PROGRESS, ghi giờ bắt đầu thực tế; "
                    + "đơn → IN_PROGRESS; xe đang AVAILABLE → IN_USE. Body không bắt buộc."
    )
    public ResponseEntity<ApiResponse<FieldMovingScheduleResponse>> start(
            @Parameter(description = "ID chuyến vận chuyển", required = true) @PathVariable Integer id,
            @Valid @RequestBody(required = false) MovingNoteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FieldMovingScheduleResponse response = fieldMovingScheduleService.start(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã bắt đầu chuyến", response));
    }

    @PostMapping("/{id}/progress")
    @Operation(
            summary = "Báo mốc tiến độ",
            description = "Chỉ giám sát viên, chuyến phải IN_PROGRESS. milestone: ARRIVED_PICKUP, LOADING_DONE, IN_TRANSIT, "
                    + "ARRIVED_DROPOFF, UNLOADING_DONE, OTHER (OTHER bắt buộc có note). Ghi vào nhật ký, không đổi trạng thái."
    )
    public ResponseEntity<ApiResponse<FieldMovingScheduleResponse>> reportProgress(
            @Parameter(description = "ID chuyến vận chuyển", required = true) @PathVariable Integer id,
            @Valid @RequestBody MovingProgressRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FieldMovingScheduleResponse response = fieldMovingScheduleService.reportProgress(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận mốc tiến độ", response));
    }

    @PatchMapping("/{id}/complete")
    @Operation(
            summary = "Hoàn tất chuyến",
            description = "Chỉ giám sát viên, chuyến phải IN_PROGRESS. Chuyến → COMPLETED, ghi giờ kết thúc thực tế; phân công "
                    + "nhân viên và xe → COMPLETED; xe IN_USE → AVAILABLE; đơn → COMPLETED nếu mọi chuyến của đơn đã xong. "
                    + "Body không bắt buộc."
    )
    public ResponseEntity<ApiResponse<FieldMovingScheduleResponse>> complete(
            @Parameter(description = "ID chuyến vận chuyển", required = true) @PathVariable Integer id,
            @Valid @RequestBody(required = false) MovingNoteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FieldMovingScheduleResponse response = fieldMovingScheduleService.complete(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã hoàn tất chuyến", response));
    }
}
