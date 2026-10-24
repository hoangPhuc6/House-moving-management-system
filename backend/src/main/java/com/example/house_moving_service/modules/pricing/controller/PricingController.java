package com.example.house_moving_service.modules.pricing.controller;

import com.example.house_moving_service.modules.pricing.dto.request.PricingCalculationRequest;
import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import com.example.house_moving_service.modules.pricing.service.PricingService;
import com.example.house_moving_service.security.access.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    @AuthenticatedUser
    @Operation(
            summary = "Tính bảng giá tham khảo dự kiến",
            description = "Tiếp nhận lộ trình, đồ đạc, lịch hẹn và voucher của khách hàng đã đăng nhập để tính toán 5 cấu phần chi phí."
    )
    public ResponseEntity<ApiResponse<PricingCalculationResponse>> estimatePrice(
            @Valid @RequestBody PricingCalculationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getId() : null;
        PricingCalculationResponse response = pricingService.calculatePrice(request, userId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
