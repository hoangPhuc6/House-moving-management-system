package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.request.pricing.PricingCalculationRequest;
import com.example.house_moving_service.dto.response.ApiResponse;
import com.example.house_moving_service.dto.response.pricing.PricingCalculationResponse;
import com.example.house_moving_service.security.CustomUserDetails;
import com.example.house_moving_service.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "APIs tính toán định giá dịch vụ và phân rã chi phí")
@SecurityRequirement(name = "bearerAuth")
public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/estimate")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Tính bảng giá tham khảo dự kiến",
            description = "Tiếp nhận lộ trình, đồ đạc, lịch hẹn và voucher của khách hàng đã đăng nhập để tính toán 5 cấu phần chi phí."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tính toán thành công, trả về bảng phân rã chi tiết"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào sai, vi phạm lịch trình hoặc voucher không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc JWT token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy dịch vụ hoặc mã voucher")
    })
    public ResponseEntity<ApiResponse<PricingCalculationResponse>> estimatePrice(
            @Valid @RequestBody PricingCalculationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        PricingCalculationResponse response = pricingService.calculatePrice(request, userId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
