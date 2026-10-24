package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.request.booking.CancelBookingRequest;
import com.example.house_moving_service.dto.request.booking.CreateBookingRequest;
import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.dto.response.booking.BookingDetailResponse;
import com.example.house_moving_service.dto.response.booking.BookingSummaryResponse;
import com.example.house_moving_service.enums.BookingStatus;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import com.example.house_moving_service.service.BookingService;
import com.example.house_moving_service.security.access.CustomerOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Slf4j
@CustomerOnly
@Tag(name = "Booking", description = "APIs quản lý đơn đặt dịch vụ chuyển nhà dành cho Khách hàng")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @Operation(
            summary = "Khởi tạo đơn đặt dịch vụ chuyển nhà mới",
            description = "Tiếp nhận thông tin lộ trình, đồ đạc, dịch vụ kèm theo và voucher. Hệ thống sẽ tự động tính toán lại chi phí bằng Pricing Engine và lưu trữ giao dịch toàn vẹn."
    )
    public ResponseEntity<ApiResponse<BookingDetailResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to create booking by user: {}", userDetails.getId());
        BookingDetailResponse response = bookingService.createBooking(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn đặt chuyển nhà thành công", response));
    }

    @GetMapping
    @Operation(
            summary = "Xem danh sách lịch sử đơn hàng của khách hàng hiện tại",
            description = "Hỗ trợ phân trang và lọc theo trạng thái đơn hàng (PENDING_SURVEY, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED)."
    )
    public ResponseEntity<ApiResponse<PageResponse<BookingSummaryResponse>>> getCustomerBookings(
            @Parameter(description = "Lọc theo trạng thái đơn hàng (tùy chọn)")
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to get bookings for user: {}, status: {}, pageable: {}", userDetails.getId(), status, pageable);
        PageResponse<BookingSummaryResponse> response = bookingService.getCustomerBookings(userDetails.getId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Xem thông tin chi tiết một đơn hàng theo ID",
            description = "Tra cứu đầy đủ thông tin đơn hàng gồm lộ trình, đồ đạc, dịch vụ, voucher và lịch sử trạng thái. Có cơ chế chống IDOR (chỉ chủ đơn mới được xem)."
    )
    public ResponseEntity<ApiResponse<BookingDetailResponse>> getBookingById(
            @Parameter(description = "Mã định danh ID của đơn hàng", required = true)
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to get booking detail id: {} for user: {}", id, userDetails.getId());
        BookingDetailResponse response = bookingService.getBookingById(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(
            summary = "Khách hàng chủ động hủy đơn hàng",
            description = "Chỉ cho phép hủy đơn khi trạng thái hiện tại là PENDING_SURVEY. Bắt buộc cung cấp lý do hủy để ghi nhận vào lịch sử chuyển đổi trạng thái."
    )
    public ResponseEntity<ApiResponse<BookingDetailResponse>> cancelBooking(
            @Parameter(description = "Mã định danh ID của đơn hàng cần hủy", required = true)
            @PathVariable Integer id,
            @Valid @RequestBody CancelBookingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to cancel booking id: {} by user: {}", id, userDetails.getId());
        BookingDetailResponse response = bookingService.cancelBooking(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công", response));
    }
}

