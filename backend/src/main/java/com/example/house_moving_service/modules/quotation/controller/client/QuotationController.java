package com.example.house_moving_service.modules.quotation.controller.client;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.quotation.dto.request.RejectQuotationRequest;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationResponse;
import com.example.house_moving_service.modules.quotation.service.QuoteService;
import com.example.house_moving_service.security.access.CustomerOnly;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/quotations")
@Slf4j
@CustomerOnly
@Tag(name = "Quotation", description = "APIs xem và phản hồi báo giá chính thức dành cho Khách hàng")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class QuotationController {

        private final QuoteService quoteService;

        @GetMapping
        @Operation(summary = "Xem các báo giá của đơn hàng", description = "Trả về các phiên bản báo giá của đơn, phiên bản mới nhất đứng đầu. Chỉ chủ đơn mới được xem.")
        public ResponseEntity<ApiResponse<List<QuotationResponse>>> getQuotations(
                        @Parameter(description = "Mã định danh ID của đơn hàng", required = true) @PathVariable Integer bookingId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to get quotations of booking id: {} by user: {}", bookingId, userDetails.getId());
                return ResponseEntity.ok(ApiResponse.ok(quoteService.getCustomerQuotations(bookingId, userDetails.getId())));
        }

        @PatchMapping("/{quotationId}/accept")
        @Operation(summary = "Chấp nhận báo giá", description = "Chỉ chấp nhận được báo giá đang SENT và còn hạn. Tổng tiền đơn được chốt theo báo giá và đơn chuyển sang APPROVED.")
        public ResponseEntity<ApiResponse<QuotationResponse>> acceptQuotation(
                        @Parameter(description = "Mã định danh ID của đơn hàng", required = true) @PathVariable Integer bookingId,
                        @Parameter(description = "Mã định danh ID của báo giá", required = true) @PathVariable Integer quotationId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to accept quotation {} of booking id: {} by user: {}", quotationId, bookingId, userDetails.getId());
                QuotationResponse response = quoteService.acceptQuotation(bookingId, quotationId, userDetails.getId());

                return ResponseEntity.ok(ApiResponse.success("Chấp nhận báo giá thành công", response));
        }

        @PatchMapping("/{quotationId}/reject")
        @Operation(summary = "Từ chối báo giá", description = "Báo giá chuyển sang REJECTED, đơn giữ trạng thái QUOTED chờ nhân viên lập báo giá mới.")
        public ResponseEntity<ApiResponse<QuotationResponse>> rejectQuotation(
                        @Parameter(description = "Mã định danh ID của đơn hàng", required = true) @PathVariable Integer bookingId,
                        @Parameter(description = "Mã định danh ID của báo giá", required = true) @PathVariable Integer quotationId,
                        @Valid @RequestBody(required = false) RejectQuotationRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                log.info("REST request to reject quotation {} of booking id: {} by user: {}", quotationId, bookingId, userDetails.getId());
                QuotationResponse response = quoteService.rejectQuotation(bookingId, quotationId, request, userDetails.getId());
                return ResponseEntity.ok(ApiResponse.success("Từ chối báo giá thành công", response));
        }
}
