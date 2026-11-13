package com.example.house_moving_service.modules.scheduling.controller.field;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.ReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.IncidentResponse;
import com.example.house_moving_service.modules.scheduling.service.FieldIncidentService;
import com.example.house_moving_service.security.access.MoverOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/field")
@MoverOnly
@RequiredArgsConstructor
@Tag(name = "Field - Incidents", description = "Nhân viên được phân công báo và xem sự cố của chuyến vận chuyển")
@SecurityRequirement(name = "bearerAuth")
public class FieldIncidentController {

    private final FieldIncidentService fieldIncidentService;

    @PostMapping("/moving-schedules/{scheduleId}/incidents")
    @Operation(
            summary = "Báo sự cố của chuyến",
            description = "Mọi nhân viên có phân công chưa hủy trên chuyến (không cần là giám sát viên). Chuyến phải CONFIRMED, "
                    + "IN_PROGRESS hoặc COMPLETED; đã COMPLETED thì chỉ trong 24 giờ (cấu hình) sau giờ kết thúc thực tế. "
                    + "incidentType: ITEM_DAMAGED, ITEM_LOST, PROPERTY_DAMAGED, VEHICLE_BREAKDOWN, TRAFFIC_ACCIDENT, DELAY, "
                    + "CUSTOMER_ISSUE, OTHER. Không đổi trạng thái chuyến hay đơn."
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> report(
            @Parameter(description = "ID chuyến vận chuyển", required = true) @PathVariable Integer scheduleId,
            @Valid @RequestBody ReportIncidentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        IncidentResponse response = fieldIncidentService.report(scheduleId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã báo sự cố", response));
    }

    @GetMapping("/moving-schedules/{scheduleId}/incidents")
    @Operation(summary = "Sự cố của chuyến", description = "Mới nhất trước. Không được phân công vào chuyến → 403.")
    public ResponseEntity<ApiResponse<List<IncidentResponse>>> getBySchedule(
            @Parameter(description = "ID chuyến vận chuyển", required = true) @PathVariable Integer scheduleId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(fieldIncidentService.getBySchedule(scheduleId, userDetails.getId())));
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Chi tiết sự cố", description = "Chỉ nhân viên được phân công vào chuyến của sự cố.")
    public ResponseEntity<ApiResponse<IncidentResponse>> getDetail(
            @Parameter(description = "ID sự cố", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(fieldIncidentService.getDetail(id, userDetails.getId())));
    }
}
