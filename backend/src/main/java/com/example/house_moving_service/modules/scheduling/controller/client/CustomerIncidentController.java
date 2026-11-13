package com.example.house_moving_service.modules.scheduling.controller.client;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.CustomerReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerIncidentResponse;
import com.example.house_moving_service.modules.scheduling.service.CustomerIncidentService;
import com.example.house_moving_service.security.access.CustomerOnly;
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
@RequestMapping("/api/v1/bookings")
@CustomerOnly
@RequiredArgsConstructor
@Tag(name = "Booking - Incidents", description = "Khách hàng xem và tự báo sự cố của đơn mình")
@SecurityRequirement(name = "bearerAuth")
public class CustomerIncidentController {

    private final CustomerIncidentService customerIncidentService;

    @GetMapping("/{bookingId}/incidents")
    @Operation(
            summary = "Sự cố của đơn",
            description = "Mới nhất trước, không gồm sự cố đã bác bỏ. Không trả người báo / người xử lý hay chi phí thiệt hại. "
                    + "Đơn không phải của mình → 403."
    )
    public ResponseEntity<ApiResponse<List<CustomerIncidentResponse>>> getIncidents(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(customerIncidentService.getIncidents(bookingId, userDetails.getId())));
    }

    @PostMapping("/{bookingId}/incidents")
    @Operation(
            summary = "Khách tự báo sự cố",
            description = "Sự cố luôn ở trạng thái REPORTED, chi phí 0; điều phối viên xác minh rồi mới giải quyết hoặc bác bỏ. "
                    + "Chỉ khi đơn IN_PROGRESS, hoặc COMPLETED trong số ngày cấu hình (mặc định 7) sau khi chuyến cuối "
                    + "kết thúc. scheduleId không bắt buộc; nếu có thì chuyến phải thuộc đơn và chưa bị hủy."
    )
    public ResponseEntity<ApiResponse<CustomerIncidentResponse>> report(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @Valid @RequestBody CustomerReportIncidentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CustomerIncidentResponse response = customerIncidentService.report(bookingId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã gửi báo cáo sự cố, công ty sẽ kiểm tra và phản hồi", response));
    }
}
