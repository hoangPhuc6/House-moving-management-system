package com.example.house_moving_service.service.impl;

import com.example.house_moving_service.dto.request.booking.BookingItemRequest;
import com.example.house_moving_service.dto.request.booking.CancelBookingRequest;
import com.example.house_moving_service.dto.request.booking.CreateBookingRequest;
import com.example.house_moving_service.dto.request.pricing.PricingCalculationRequest;
import com.example.house_moving_service.dto.request.pricing.ServiceItemDto;
import com.example.house_moving_service.dto.response.PageResponse;
import com.example.house_moving_service.dto.response.booking.BookingDetailResponse;
import com.example.house_moving_service.dto.response.booking.BookingSummaryResponse;
import com.example.house_moving_service.dto.response.pricing.BreakdownItemDto;
import com.example.house_moving_service.dto.response.pricing.PricingCalculationResponse;
import com.example.house_moving_service.entity.Booking;
import com.example.house_moving_service.entity.BookingItem;
import com.example.house_moving_service.entity.BookingRoute;
import com.example.house_moving_service.entity.BookingStatusHistory;
import com.example.house_moving_service.entity.BookingVoucher;
import com.example.house_moving_service.entity.Customer;
import com.example.house_moving_service.entity.Service;
import com.example.house_moving_service.entity.User;
import com.example.house_moving_service.entity.Voucher;
import com.example.house_moving_service.enums.BookingStatus;
import com.example.house_moving_service.exception.AppException;
import com.example.house_moving_service.exception.ErrorCode;
import com.example.house_moving_service.mapper.BookingMapper;
import com.example.house_moving_service.repository.BookingItemRepository;
import com.example.house_moving_service.repository.BookingRepository;
import com.example.house_moving_service.repository.BookingRouteRepository;
import com.example.house_moving_service.repository.BookingServiceRepository;
import com.example.house_moving_service.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.repository.BookingVoucherRepository;
import com.example.house_moving_service.repository.CustomerRepository;
import com.example.house_moving_service.repository.ServiceRepository;
import com.example.house_moving_service.repository.UserRepository;
import com.example.house_moving_service.repository.VoucherRepository;
import com.example.house_moving_service.service.BookingService;
import com.example.house_moving_service.service.PricingService;
import com.example.house_moving_service.util.BookingCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4J
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingRouteRepository bookingRouteRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingServiceRepository bookingServiceRepository;
    private final BookingVoucherRepository bookingVoucherRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final VoucherRepository voucherRepository;
    private final ServiceRepository serviceRepository;
    private final PricingService pricingService;
    private final BookingCodeGenerator bookingCodeGenerator;
    private final BookingMapper bookingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse createBooking(CreateBookingRequest request, Long currentUserId) {
        log.info("Starting createBooking transaction for user: {}", currentUserId);

        // 1. Xác thực danh tính người dùng và khách hàng
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_UNAUTHORIZED));

        Customer customer = customerRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_CUSTOMER_NOT_FOUND));

        // 2. Kiểm tra Voucher (nếu có cung cấp mã voucher)
        Voucher voucher = null;
        if (request.getVoucherCode() != null && !request.getVoucherCode().trim().isEmpty()) {
            String code = request.getVoucherCode().trim().toUpperCase();

            voucher = voucherRepository.findByCodeWithLock(code)
                    .orElseThrow(() -> new AppException(ErrorCode.ERR_VOUCHER_NOT_FOUND, code));

            LocalDateTime now = LocalDateTime.now();
            if (!Boolean.TRUE.equals(voucher.getIsActive())
                    || now.isBefore(voucher.getStartDate())
                    || now.isAfter(voucher.getEndDate())) {
                throw new AppException(ErrorCode.ERR_VOUCHER_EXPIRED, code);
            }

            // Kiểm tra tổng lượt dùng toàn hệ thống
            if (voucher.getTotalUsageLimit() != null) {
                long totalUsed = bookingVoucherRepository.countByVoucherId(voucher.getId());
                if (totalUsed >= voucher.getTotalUsageLimit()) {
                    throw new AppException(ErrorCode.ERR_VOUCHER_LIMIT_REACHED);
                }
            }

            // Kiểm tra lượt dùng của từng khách hàng
            if (voucher.getUsagePerUserLimit() != null) {
                long userUsed = bookingVoucherRepository.countByVoucherIdAndBooking_CustomerId(voucher.getId(), customer.getId());
                if (userUsed >= voucher.getUsagePerUserLimit()) {
                    throw new AppException(ErrorCode.ERR_VOUCHER_LIMIT_REACHED);
                }
            }
        }

        // 3. Gọi ngầm PricingService (Module 2) để tính giá chính thức
        PricingCalculationRequest pricingRequest = PricingCalculationRequest.builder()
                .schedule(request.getSchedule())
                .route(request.getRoute())
                .serviceItems(request.getServiceItems())
                .voucherCode(request.getVoucherCode())
                .build();

        PricingCalculationResponse pricingResponse = pricingService.calculatePrice(pricingRequest, currentUserId);

        BigDecimal finalTotal = pricingResponse.getFinancialSummary().getFinalTotal();
        BigDecimal discountAmount = pricingResponse.getFinancialSummary().getDiscountAmount();
        BigDecimal calculatedDistanceKm = pricingResponse.getCalculatedDistanceKm();

        // 4. Khởi tạo & Lưu bảng chính 1: bookings
        String bookingCode = bookingCodeGenerator.generateUniqueBookingCode();

        Booking booking = Booking.builder()
                .bookingCode(bookingCode)
                .customer(customer)
                .createdBy(currentUser)
                .totalAmount(finalTotal)
                .discountAmount(discountAmount != null ? discountAmount : BigDecimal.ZERO)
                .note(request.getNote())
                .status(BookingStatus.PENDING_SURVEY)
                .desiredStartAt(request.getSchedule().getDesiredStartAt())
                .desiredEndAt(request.getSchedule().getDesiredEndAt())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        log.debug("Saved booking header with id: {}, code: {}", savedBooking.getId(), savedBooking.getBookingCode());

        // 5. Lưu bảng 2: booking_routes
        var pickup = request.getRoute().getPickup();
        var dropoff = request.getRoute().getDropoff();

        String pickupNote = pickup != null
                ? String.format("Tầng: %d, Thang máy: %s, Cự ly hẻm: %dm",
                pickup.getFloorNumber() != null ? pickup.getFloorNumber() : 0,
                Boolean.TRUE.equals(pickup.getHasElevator()) ? "Có" : "Không",
                pickup.getWalkingDistanceMeters() != null ? pickup.getWalkingDistanceMeters() : 0)
                : null;

        String dropoffNote = dropoff != null
                ? String.format("Tầng: %d, Thang máy: %s, Cự ly hẻm: %dm",
                dropoff.getFloorNumber() != null ? dropoff.getFloorNumber() : 0,
                Boolean.TRUE.equals(dropoff.getHasElevator()) ? "Có" : "Không",
                dropoff.getWalkingDistanceMeters() != null ? dropoff.getWalkingDistanceMeters() : 0)
                : null;

        BookingRoute route = BookingRoute.builder()
                .booking(savedBooking)
                .pickupAddress(pickup != null ? pickup.getAddress() : null)
                .pickupLat(pickup != null ? pickup.getLat() : null)
                .pickupLng(pickup != null ? pickup.getLng() : null)
                .pickupPropertyType(pickup != null ? pickup.getPropertyType() : null)
                .pickupNote(pickupNote)
                .dropoffAddress(dropoff != null ? dropoff.getAddress() : null)
                .dropoffLat(dropoff != null ? dropoff.getLat() : null)
                .dropoffLng(dropoff != null ? dropoff.getLng() : null)
                .dropoffPropertyType(dropoff != null ? dropoff.getPropertyType() : null)
                .dropoffNote(dropoffNote)
                .estimatedDistanceKm(calculatedDistanceKm)
                .build();

        BookingRoute savedRoute = bookingRouteRepository.save(route);

        // 6. Lưu bảng 3: booking_items
        List<BookingItem> savedItems = new ArrayList<>();
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            List<BookingItem> itemsToSave = request.getItems().stream()
                    .map(itemReq -> BookingItem.builder()
                            .booking(savedBooking)
                            .itemName(itemReq.getItemName())
                            .quantity(itemReq.getQuantity() != null ? itemReq.getQuantity() : 1)
                            .estimatedVolumeM3(itemReq.getEstimatedVolumeM3())
                            .isFragile(Boolean.TRUE.equals(itemReq.getIsFragile()))
                            .packingRequired(Boolean.TRUE.equals(itemReq.getPackingRequired()))
                            .disassemblyRequired(Boolean.TRUE.equals(itemReq.getDisassemblyRequired()))
                            .note(itemReq.getNote())
                            .build())
                    .toList();
            savedItems = bookingItemRepository.saveAll(itemsToSave);
        }

        // 7. Lưu bảng 4: booking_services (SNAPSHOT giá tại thời điểm chốt đơn)
        List<com.example.house_moving_service.entity.BookingService> bookingServicesToSave = new ArrayList<>();
        if (pricingResponse.getBreakdownItems() != null) {
            for (BreakdownItemDto breakdown : pricingResponse.getBreakdownItems()) {
                Service serviceProxy = serviceRepository.getReferenceById(breakdown.getServiceId());
                int quantity = breakdown.getQuantity() != null ? breakdown.getQuantity().intValue() : 1;

                com.example.house_moving_service.entity.BookingService bs = com.example.house_moving_service.entity.BookingService.builder()
                        .booking(savedBooking)
                        .service(serviceProxy)
                        .priceApplied(breakdown.getUnitPrice()) // Snapshot đơn giá chuẩn
                        .quantity(quantity)
                        .note(breakdown.getNote())
                        .build();

                bookingServicesToSave.add(bs);
            }
        }
        List<com.example.house_moving_service.entity.BookingService> savedServices = bookingServiceRepository.saveAll(bookingServicesToSave);

        // 8. Lưu bảng 5: booking_vouchers (Nếu có áp dụng voucher thành công)
        List<BookingVoucher> savedVouchers = new ArrayList<>();
        if (pricingResponse.getAppliedVoucher() != null && pricingResponse.getAppliedVoucher().getVoucherId() != null) {
            Voucher appliedVoucherRef = voucherRepository.getReferenceById(pricingResponse.getAppliedVoucher().getVoucherId());
            BookingVoucher bookingVoucher = BookingVoucher.builder()
                    .booking(savedBooking)
                    .voucher(appliedVoucherRef)
                    .discountAmount(pricingResponse.getAppliedVoucher().getDiscountAmount())
                    .build();

            BookingVoucher savedBookingVoucher = bookingVoucherRepository.save(bookingVoucher);
            savedVouchers.add(savedBookingVoucher);

            // Cập nhật used_count của Voucher
            if (voucher != null) {
                int currentUsedCount = voucher.getUsedCount() != null ? voucher.getUsedCount() : 0;
                voucher.setUsedCount(currentUsedCount + 1);
                voucherRepository.save(voucher);
            }
        }

        // 9. Lưu bảng 6: booking_status_history
        BookingStatusHistory history = BookingStatusHistory.builder()
                .booking(savedBooking)
                .oldStatus(null)
                .newStatus(BookingStatus.PENDING_SURVEY)
                .changedBy(currentUser)
                .note("Khách hàng tạo đơn")
                .build();

        BookingStatusHistory savedHistory = bookingStatusHistoryRepository.save(history);
        List<BookingStatusHistory> savedHistories = savedHistory != null ? List.of(savedHistory) : Collections.emptyList();

        log.info("Successfully created booking code: {} across all 6 tables", savedBooking.getBookingCode());

        // 10. Trả về Response DTO trọn vẹn, không kích hoạt Lazy load
        return bookingMapper.toDetailResponse(
                savedBooking,
                savedRoute,
                savedItems,
                savedServices,
                savedVouchers,
                savedHistories
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDetailResponse getBookingById(Integer id, Long currentUserId) {
        throw new UnsupportedOperationException("Tính năng getBookingById sẽ được triển khai trong TASK-3.4");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> getCustomerBookings(Long currentUserId, Pageable pageable) {
        throw new UnsupportedOperationException("Tính năng getCustomerBookings sẽ được triển khai trong TASK-3.4");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse cancelBooking(Integer id, CancelBookingRequest request, Long currentUserId) {
        throw new UnsupportedOperationException("Tính năng cancelBooking sẽ được triển khai trong TASK-3.4");
    }
}
