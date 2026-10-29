package com.example.house_moving_service.modules.booking.controller.management;

import com.example.house_moving_service.modules.booking.dto.request.BookingManagementFilterRequest;
import com.example.house_moving_service.modules.booking.dto.request.CancelBookingRequest;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingDetailResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSummaryResponse;
import com.example.house_moving_service.modules.identity.entity.Role;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import com.example.house_moving_service.modules.booking.service.BookingManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ManagementBookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private BookingManagementService bookingManagementService;

    private CustomUserDetails createMockUserDetails(Long id, String email, RoleCode roleCode) {
        Role role = Role.builder()
                .id(1L)
                .code(roleCode)
                .name(roleCode.name())
                .build();

        User user = User.builder()
                .id(id)
                .email(email)
                .password("encoded_pass")
                .isActive(true)
                .roles(Set.of(role))
                .build();

        return new CustomUserDetails(user);
    }

    private BookingSummaryResponse createMockSummary(Integer id, String code, BookingStatus status) {
        return BookingSummaryResponse.builder()
                .id(id)
                .bookingCode(code)
                .status(status)
                .totalAmount(new BigDecimal("2000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .pickupAddress("123 Lê Lợi, Q1, TP.HCM")
                .dropoffAddress("456 Nguyễn Thị Minh Khai, Q3, TP.HCM")
                .desiredStartAt(LocalDateTime.now().plusDays(2))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private BookingDetailResponse createMockDetail(Integer id, String code, BookingStatus status) {
        return BookingDetailResponse.builder()
                .id(id)
                .bookingCode(code)
                .customerId(99L)
                .customerName("Khách Hàng Mẫu")
                .contactName("Khách Hàng Mẫu")
                .contactPhone("0987654321")
                .status(status)
                .totalAmount(new BigDecimal("2000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .note("Ghi chú khảo sát nội bộ")
                .desiredStartAt(LocalDateTime.now().plusDays(2))
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // 1. GET /api/v1/management/bookings (Tìm kiếm & phân trang)
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/management/bookings - Admin truy cập thành công -> Trả về 200 OK")
    void searchBookings_AdminRole_Returns200() throws Exception {
        CustomUserDetails admin = createMockUserDetails(1L, "admin@example.com", RoleCode.ADMIN);
        BookingSummaryResponse summary = createMockSummary(1, "BKG2610190001", BookingStatus.PENDING_SURVEY);
        PageResponse<BookingSummaryResponse> pageResponse = PageResponse.<BookingSummaryResponse>builder()
                .pageNo(1)
                .pageSize(10)
                .totalPages(1)
                .totalElements(1)
                .last(true)
                .items(List.of(summary))
                .build();

        when(bookingManagementService.searchAllBookings(any(BookingManagementFilterRequest.class), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/management/bookings")
                        .with(user(admin))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.items[0].bookingCode").value("BKG2610190001"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/management/bookings - Dispatcher truy cập thành công -> Trả về 200 OK")
    void searchBookings_DispatcherRole_Returns200() throws Exception {
        CustomUserDetails dispatcher = createMockUserDetails(2L, "dispatcher@example.com", RoleCode.DISPATCHER);
        BookingSummaryResponse summary = createMockSummary(2, "BKG2610190002", BookingStatus.CONFIRMED);
        PageResponse<BookingSummaryResponse> pageResponse = PageResponse.<BookingSummaryResponse>builder()
                .pageNo(1)
                .pageSize(10)
                .totalPages(1)
                .totalElements(1)
                .last(true)
                .items(List.of(summary))
                .build();

        when(bookingManagementService.searchAllBookings(any(BookingManagementFilterRequest.class), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/management/bookings")
                        .with(user(dispatcher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.items[0].bookingCode").value("BKG2610190002"));
    }

    @Test
    @DisplayName("GET /api/v1/management/bookings - Lọc với tham số status & bookingCode -> Trả về 200 OK")
    void searchBookings_WithFilters_Returns200() throws Exception {
        CustomUserDetails admin = createMockUserDetails(1L, "admin@example.com", RoleCode.ADMIN);
        PageResponse<BookingSummaryResponse> emptyPage = PageResponse.<BookingSummaryResponse>builder()
                .pageNo(1)
                .pageSize(10)
                .totalPages(0)
                .totalElements(0)
                .last(true)
                .items(List.of())
                .build();

        when(bookingManagementService.searchAllBookings(any(BookingManagementFilterRequest.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/management/bookings")
                        .with(user(admin))
                        .param("status", "CONFIRMED")
                        .param("bookingCode", "BKG261019"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/management/bookings - Role CUSTOMER gọi API quản trị -> Bị chặn 403 Forbidden")
    void searchBookings_CustomerForbidden_Returns403() throws Exception {
        CustomUserDetails customer = createMockUserDetails(10L, "customer@example.com", RoleCode.CUSTOMER);

        mockMvc.perform(get("/api/v1/management/bookings")
                        .with(user(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /api/v1/management/bookings - Không có token -> Trả về 401 Unauthorized")
    void searchBookings_Unauthorized_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/management/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.getCode()));
    }

    // =========================================================================
    // 2. GET /api/v1/management/bookings/{id} (Chi tiết đơn hàng)
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/management/bookings/{id} - Admin xem chi tiết đơn bất kỳ -> Trả về 200 OK")
    void getBookingDetail_AdminCanViewAnyBooking_Returns200() throws Exception {
        CustomUserDetails admin = createMockUserDetails(1L, "admin@example.com", RoleCode.ADMIN);
        BookingDetailResponse detail = createMockDetail(100, "BKG100", BookingStatus.PENDING_SURVEY);

        when(bookingManagementService.getBookingDetailByManagement(100)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/management/bookings/100")
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.bookingCode").value("BKG100"));
    }

    @Test
    @DisplayName("GET /api/v1/management/bookings/{id} - Dispatcher xem chi tiết đơn -> Trả về 200 OK")
    void getBookingDetail_DispatcherCanViewAnyBooking_Returns200() throws Exception {
        CustomUserDetails dispatcher = createMockUserDetails(2L, "dispatcher@example.com", RoleCode.DISPATCHER);
        BookingDetailResponse detail = createMockDetail(100, "BKG100", BookingStatus.PENDING_SURVEY);

        when(bookingManagementService.getBookingDetailByManagement(100)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/management/bookings/100")
                        .with(user(dispatcher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("GET /api/v1/management/bookings/{id} - Không tìm thấy đơn hàng -> Trả về 404 Not Found")
    void getBookingDetail_NotFound_Returns404() throws Exception {
        CustomUserDetails admin = createMockUserDetails(1L, "admin@example.com", RoleCode.ADMIN);

        when(bookingManagementService.getBookingDetailByManagement(999))
                .thenThrow(new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, 999));

        mockMvc.perform(get("/api/v1/management/bookings/999")
                        .with(user(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_BOOKING_NOT_FOUND.getCode()));
    }

    // =========================================================================
    // 3. PATCH /api/v1/management/bookings/{id}/cancel (Hủy đơn hàng quản trị)
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/v1/management/bookings/{id}/cancel - Admin hủy đơn CONFIRMED -> Trả về 200 OK")
    void cancelBooking_ManagementCancelConfirmedOrder_Returns200() throws Exception {
        CustomUserDetails admin = createMockUserDetails(1L, "admin@example.com", RoleCode.ADMIN);
        CancelBookingRequest request = CancelBookingRequest.builder()
                .reason("Khách hàng báo hủy gấp do thay đổi ngày nhận nhà")
                .build();

        BookingDetailResponse detail = createMockDetail(100, "BKG100", BookingStatus.CANCELLED);

        when(bookingManagementService.cancelBookingByManagement(eq(100), any(CancelBookingRequest.class), eq(1L)))
                .thenReturn(detail);

        mockMvc.perform(patch("/api/v1/management/bookings/100/cancel")
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Hủy đơn hàng thành công"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/management/bookings/{id}/cancel - Đơn đã bị CANCELLED từ trước -> Trả về 400 Bad Request")
    void cancelBooking_AlreadyCancelled_Returns400() throws Exception {
        CustomUserDetails admin = createMockUserDetails(1L, "admin@example.com", RoleCode.ADMIN);
        CancelBookingRequest request = CancelBookingRequest.builder()
                .reason("Hủy đơn đã bị hủy")
                .build();

        when(bookingManagementService.cancelBookingByManagement(eq(100), any(CancelBookingRequest.class), eq(1L)))
                .thenThrow(new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS));

        mockMvc.perform(patch("/api/v1/management/bookings/100/cancel")
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_BOOKING_INVALID_STATUS.getCode()));
    }
}

