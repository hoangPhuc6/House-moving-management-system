package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.request.booking.BookingItemRequest;
import com.example.house_moving_service.dto.request.booking.CancelBookingRequest;
import com.example.house_moving_service.dto.request.booking.CreateBookingRequest;
import com.example.house_moving_service.dto.request.pricing.LocationDto;
import com.example.house_moving_service.dto.request.pricing.RouteDto;
import com.example.house_moving_service.dto.request.pricing.ScheduleDto;
import com.example.house_moving_service.dto.request.pricing.ServiceItemDto;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.dto.response.booking.BookingDetailResponse;
import com.example.house_moving_service.dto.response.booking.BookingSummaryResponse;
import com.example.house_moving_service.modules.identity.entity.Role;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.enums.BookingStatus;
import com.example.house_moving_service.common.enums.PropertyType;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import com.example.house_moving_service.service.BookingService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private BookingService bookingService;

    private CustomUserDetails createMockCustomerUserDetails(Long id, String email) {
        Role customerRole = Role.builder()
                .id(4L)
                .code(RoleCode.CUSTOMER)
                .name("CUSTOMER")
                .build();

        User user = User.builder()
                .id(id)
                .email(email)
                .password("encoded_pass")
                .isActive(true)
                .roles(Set.of(customerRole))
                .build();

        return new CustomUserDetails(user);
    }

    private CustomUserDetails createMockAdminUserDetails(Long id, String email) {
        Role adminRole = Role.builder()
                .id(1L)
                .code(RoleCode.ADMIN)
                .name("ADMIN")
                .build();

        User user = User.builder()
                .id(id)
                .email(email)
                .password("encoded_pass")
                .isActive(true)
                .roles(Set.of(adminRole))
                .build();

        return new CustomUserDetails(user);
    }

    private CreateBookingRequest createValidCreateRequest() {
        LocalDateTime now = LocalDateTime.now();
        return CreateBookingRequest.builder()
                .contactName("Nguyễn Văn A")
                .contactPhone("0987654321")
                .note("Dọn nhà cẩn thận, tránh va đập")
                .schedule(ScheduleDto.builder()
                        .desiredStartAt(now.plusDays(1))
                        .desiredEndAt(now.plusDays(1).plusHours(4))
                        .build())
                .route(RouteDto.builder()
                        .pickup(LocationDto.builder()
                                .address("123 Lê Lợi, Q1, TP.HCM")
                                .lat(new BigDecimal("10.776889"))
                                .lng(new BigDecimal("106.700806"))
                                .propertyType(PropertyType.HOUSE)
                                .floorNumber(0)
                                .hasElevator(true)
                                .walkingDistanceMeters(0)
                                .build())
                        .dropoff(LocationDto.builder()
                                .address("456 Nguyễn Thị Minh Khai, Q3, TP.HCM")
                                .lat(new BigDecimal("10.782622"))
                                .lng(new BigDecimal("106.695844"))
                                .propertyType(PropertyType.APARTMENT)
                                .floorNumber(2)
                                .hasElevator(false)
                                .walkingDistanceMeters(20)
                                .build())
                        .manualDistanceKm(new BigDecimal("15.00"))
                        .build())
                .serviceItems(List.of(
                        ServiceItemDto.builder().serviceId(1).quantity(new BigDecimal("1")).build()
                ))
                .items(List.of(
                        BookingItemRequest.builder()
                                .itemName("Tủ lạnh Samsung 300L")
                                .quantity(1)
                                .estimatedVolumeM3(new BigDecimal("1.20"))
                                .isFragile(false)
                                .packingRequired(true)
                                .disassemblyRequired(false)
                                .note("Cần bọc màng PE chống xước")
                                .build()
                ))
                .voucherCode("GIAM50K")
                .build();
    }

    private BookingDetailResponse createMockDetailResponse(Integer id, Long customerId, String code, BookingStatus bookingStatus) {
        return BookingDetailResponse.builder()
                .id(id)
                .bookingCode(code)
                .customerId(customerId)
                .customerName("Nguyễn Văn A")
                .contactName("Nguyễn Văn A")
                .contactPhone("0987654321")
                .status(bookingStatus)
                .totalAmount(new BigDecimal("1500000.00"))
                .discountAmount(new BigDecimal("50000.00"))
                .note("Dọn nhà cẩn thận")
                .desiredStartAt(LocalDateTime.now().plusDays(1))
                .desiredEndAt(LocalDateTime.now().plusDays(1).plusHours(4))
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // 1. POST /api/v1/bookings (Tạo mới đơn hàng)
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/bookings - Thành công với quyền CUSTOMER -> Trả về 201 Created")
    void createBooking_Success_Returns201Created() throws Exception {
        CreateBookingRequest request = createValidCreateRequest();
        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");
        BookingDetailResponse response = createMockDetailResponse(1, 10L, "BKG2610190001", BookingStatus.PENDING_SURVEY);

        when(bookingService.createBooking(any(CreateBookingRequest.class), eq(10L))).thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Tạo đơn đặt chuyển nhà thành công"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.bookingCode").value("BKG2610190001"))
                .andExpect(jsonPath("$.data.status").value("PENDING_SURVEY"))
                .andExpect(jsonPath("$.data.totalAmount").value("1500000.00"));
    }

    @Test
    @DisplayName("POST /api/v1/bookings - Không có token -> Trả về 401 Unauthorized")
    void createBooking_Unauthorized_WhenNoToken_Returns401() throws Exception {
        CreateBookingRequest request = createValidCreateRequest();

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/bookings - User có role ADMIN gọi API CUSTOMER -> Trả về 403 Forbidden")
    void createBooking_WrongRoleAdmin_Returns403Forbidden() throws Exception {
        CreateBookingRequest request = createValidCreateRequest();
        CustomUserDetails adminDetails = createMockAdminUserDetails(1L, "admin@example.com");

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(adminDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("POST /api/v1/bookings - Số điện thoại liên hệ sai định dạng -> Trả về 400 Bad Request")
    void createBooking_InvalidPhone_Returns400BadRequest() throws Exception {
        CreateBookingRequest request = createValidCreateRequest();
        request.setContactPhone("123456"); // Sai Regex số điện thoại VN

        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.errors.contactPhone").exists());
    }

    @Test
    @DisplayName("POST /api/v1/bookings - Thiếu thông tin lộ trình (Route rỗng) -> Trả về 400 Bad Request")
    void createBooking_MissingRoute_Returns400BadRequest() throws Exception {
        CreateBookingRequest request = createValidCreateRequest();
        request.setRoute(null); // Vi phạm @NotNull

        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.errors.route").exists());
    }

    // =========================================================================
    // 2. GET /api/v1/bookings (Lấy danh sách phân trang)
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/bookings - Thành công lấy danh sách có phân trang -> Trả về 200 OK")
    void getCustomerBookings_Success_Returns200WithPagination() throws Exception {
        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");

        BookingSummaryResponse summary = BookingSummaryResponse.builder()
                .id(1)
                .bookingCode("BKG2610190001")
                .status(BookingStatus.PENDING_SURVEY)
                .totalAmount(new BigDecimal("1500000.00"))
                .discountAmount(new BigDecimal("50000.00"))
                .pickupAddress("123 Lê Lợi, Q1, TP.HCM")
                .dropoffAddress("456 Nguyễn Thị Minh Khai, Q3, TP.HCM")
                .desiredStartAt(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now())
                .build();

        PageResponse<BookingSummaryResponse> pageResponse = PageResponse.<BookingSummaryResponse>builder()
                .pageNo(1)
                .pageSize(10)
                .totalPages(1)
                .totalElements(1)
                .last(true)
                .items(List.of(summary))
                .build();

        when(bookingService.getCustomerBookings(eq(10L), eq(null), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/bookings")
                        .with(user(userDetails))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].bookingCode").value("BKG2610190001"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.pageNo").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/bookings?status=PENDING_SURVEY - Lọc theo status -> Trả về 200 OK")
    void getCustomerBookings_WithStatusFilter_Returns200() throws Exception {
        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");

        PageResponse<BookingSummaryResponse> emptyPage = PageResponse.<BookingSummaryResponse>builder()
                .pageNo(1)
                .pageSize(10)
                .totalPages(0)
                .totalElements(0)
                .last(true)
                .items(List.of())
                .build();

        when(bookingService.getCustomerBookings(eq(10L), eq(BookingStatus.PENDING_SURVEY), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/bookings")
                        .with(user(userDetails))
                        .param("status", "PENDING_SURVEY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // =========================================================================
    // 3. GET /api/v1/bookings/{id} (Chi tiết đơn hàng)
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/bookings/{id} - Thành công lấy chi tiết đơn -> Trả về 200 OK")
    void getBookingById_Success_Returns200() throws Exception {
        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");
        BookingDetailResponse response = createMockDetailResponse(1, 10L, "BKG2610190001", BookingStatus.PENDING_SURVEY);

        when(bookingService.getBookingById(eq(1), eq(10L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/bookings/1")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.bookingCode").value("BKG2610190001"));
    }

    @Test
    @DisplayName("GET /api/v1/bookings/{id} - Khách khác cố tình truy cập đơn (IDOR) -> Bị chặn 403 Forbidden")
    void getBookingById_ForbiddenIdor_Returns403() throws Exception {
        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");

        when(bookingService.getBookingById(eq(99), eq(10L)))
                .thenThrow(new AppException(ErrorCode.ERR_BOOKING_FORBIDDEN));

        mockMvc.perform(get("/api/v1/bookings/99")
                        .with(user(userDetails)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_BOOKING_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /api/v1/bookings/{id} - Không tìm thấy đơn hàng -> Trả về 404 Not Found")
    void getBookingById_NotFound_Returns404() throws Exception {
        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");

        when(bookingService.getBookingById(eq(999), eq(10L)))
                .thenThrow(new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, 999));

        mockMvc.perform(get("/api/v1/bookings/999")
                        .with(user(userDetails)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_BOOKING_NOT_FOUND.getCode()));
    }

    // =========================================================================
    // 4. PATCH /api/v1/bookings/{id}/cancel (Khách hủy đơn hàng)
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/v1/bookings/{id}/cancel - Thành công hủy đơn -> Trả về 200 OK")
    void cancelBooking_Success_Returns200() throws Exception {
        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");
        CancelBookingRequest request = CancelBookingRequest.builder()
                .reason("Khách hàng thay đổi kế hoạch dọn nhà")
                .build();

        BookingDetailResponse response = createMockDetailResponse(1, 10L, "BKG2610190001", BookingStatus.CANCELLED);

        when(bookingService.cancelBooking(eq(1), any(CancelBookingRequest.class), eq(10L)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/bookings/1/cancel")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Hủy đơn hàng thành công"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/bookings/{id}/cancel - Thiếu lý do hủy (reason rỗng) -> Trả về 400 Bad Request")
    void cancelBooking_BlankReason_Returns400BadRequest() throws Exception {
        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");
        CancelBookingRequest request = CancelBookingRequest.builder()
                .reason("") // Vi phạm @NotBlank
                .build();

        mockMvc.perform(patch("/api/v1/bookings/1/cancel")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.errors.reason").exists());
    }

    @Test
    @DisplayName("PATCH /api/v1/bookings/{id}/cancel - Đơn đã ở trạng thái CONFIRMED -> Trả về 400 Bad Request")
    void cancelBooking_InvalidStatus_Returns400BadRequest() throws Exception {
        CustomUserDetails userDetails = createMockCustomerUserDetails(10L, "customer@example.com");
        CancelBookingRequest request = CancelBookingRequest.builder()
                .reason("Muốn hủy đơn gấp")
                .build();

        when(bookingService.cancelBooking(eq(1), any(CancelBookingRequest.class), eq(10L)))
                .thenThrow(new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS));

        mockMvc.perform(patch("/api/v1/bookings/1/cancel")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_BOOKING_INVALID_STATUS.getCode()));
    }
}
