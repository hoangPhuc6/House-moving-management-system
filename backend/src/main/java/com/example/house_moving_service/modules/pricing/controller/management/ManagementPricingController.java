package com.example.house_moving_service.modules.pricing.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.pricing.dto.response.QuotationResponse;
import com.example.house_moving_service.modules.pricing.service.QuoteService;
import com.example.house_moving_service.modules.pricing.dto.request.CreateQuoteRequest;
import com.example.house_moving_service.security.access.StaffOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/api/v1/management/pricing")
@Slf4j
@StaffOnly
@Tag(name = "Management - Pricing", description = "APIs lập báo giá chính thức sau khảo sát dành cho Quản trị viên & Điều phối viên")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ManagementPricingController {

        private final QuoteService quoteService;

        @PostMapping("/bookings/{id}/quote")
        @Operation(summary = "Lập báo giá chính thức sau khảo sát", description = "Tính lại giá theo danh sách dịch vụ, đồ đạc và cự ly thực tế sau khảo sát; tạo phiên bản báo giá mới (SENT) gửi khách và chuyển đơn sang QUOTED. Báo giá đang chờ trước đó chuyển sang EXPIRED.")
        public ResponseEntity<ApiResponse<QuotationResponse>> createQuote(
                        @Parameter(description = "Mã định danh ID của đơn hàng cần báo giá", required = true) @PathVariable Integer id,
                        @Valid @RequestBody CreateQuoteRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("Management request to create quote for booking id: {} by staff user: {}", id, userDetails.getId());
                QuotationResponse response = quoteService.createQuote(id, request, userDetails.getId());
                return ResponseEntity.ok(ApiResponse.success("Lập báo giá thành công", response));
        }

        @GetMapping("/bookings/{id}/quotations")
        @Operation(summary = "Xem các phiên bản báo giá của đơn hàng", description = "Trả về tất cả phiên bản báo giá, phiên bản mới nhất đứng đầu.")
        public ResponseEntity<ApiResponse<List<QuotationResponse>>> getQuotations(
                        @Parameter(description = "Mã định danh ID của đơn hàng", required = true) @PathVariable Integer id) {
                log.info("Management request to get quotations for booking id: {}", id);
                return ResponseEntity.ok(ApiResponse.ok(quoteService.getQuotations(id)));
        }
}
