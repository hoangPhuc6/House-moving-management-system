package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.request.booking.BookingManagementFilterRequest;
import com.example.house_moving_service.dto.request.booking.CancelBookingRequest;
import com.example.house_moving_service.dto.response.ApiResponse;
import com.example.house_moving_service.dto.response.PageResponse;
import com.example.house_moving_service.dto.response.booking.BookingDetailResponse;
import com.example.house_moving_service.dto.response.booking.BookingSummaryResponse;
import com.example.house_moving_service.security.CustomUserDetails;
import com.example.house_moving_service.service.BookingManagementService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management/bookings")
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
@Tag(name = "Management - Bookings", description = "APIs quản lý và điều phối đơn hàng dành cho Quản trị viên & Điều phối viên")
@SecurityRequirement(name = "bearerAuth")
public class ManagementBookingController {

    private final BookingManagementService bookingManagementService;

    public ManagementBookingController(BookingManagementService bookingManagementService) {
        this.bookingManagementService = bookingManagementService;
    }

    @GetMapping
    @Operation(
            summary = "Tìm kiếm và lọc danh sách đơn hàng toàn hệ thống",
            description = "Hỗ trợ lọc đa tiêu chí bằng JPA Specification: theo trạng thái, mã đơn, số điện thoại, tên khách, khoảng thời gian tạo đơn kèm phân trang."
    )
    public ResponseEntity<ApiResponse<PageResponse<BookingSummaryResponse>>> searchBookings(
            BookingManagementFilterRequest filter,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Management search bookings request with pageable: {}", pageable);
        PageResponse<BookingSummaryResponse> response = bookingManagementService.searchAllBookings(filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Xem thông tin chi tiết một đơn hàng bất kỳ",
            description = "Tra cứu toàn diện thông tin đơn hàng mà không bị hạn chế bởi IDOR (Admin & Dispatcher có toàn quyền xem)."
    )
    public ResponseEntity<ApiResponse<BookingDetailResponse>> getBookingDetail(
            @Parameter(description = "Mã định danh ID của đơn hàng", required = true)
            @PathVariable Integer id) {
        log.info("Management request to get booking detail for id: {}", id);
        BookingDetailResponse response = bookingManagementService.getBookingDetailByManagement(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(
            summary = "Quản trị viên / Điều phối viên hủy đơn hàng",
            description = "Cho phép can thiệp hủy đơn ở mọi giai đoạn (ngoại trừ đơn đã bị CANCELLED). Ghi nhận người thực hiện hủy là Admin/Dispatcher."
    )
    public ResponseEntity<ApiResponse<BookingDetailResponse>> cancelBooking(
            @Parameter(description = "Mã định danh ID của đơn hàng cần hủy", required = true)
            @PathVariable Integer id,
            @Valid @RequestBody CancelBookingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Management request to cancel booking id: {} by staff user: {}", id, userDetails.getId());
        BookingDetailResponse response = bookingManagementService.cancelBookingByManagement(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công", response));
    }
}

