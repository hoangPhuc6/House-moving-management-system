package com.example.house_moving_service.modules.booking.service.impl;

import com.example.house_moving_service.modules.booking.dto.request.CancelBookingRequest;
import com.example.house_moving_service.modules.booking.dto.request.CreateBookingRequest;
import com.example.house_moving_service.modules.pricing.dto.request.PricingCalculationRequest;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingDetailResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSummaryResponse;
import com.example.house_moving_service.modules.pricing.dto.response.BreakdownItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingItem;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.entity.BookingVoucher;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.mapper.BookingMapper;
import com.example.house_moving_service.modules.booking.repository.BookingItemRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRouteRepository;
import com.example.house_moving_service.modules.booking.repository.BookingServiceRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.repository.BookingVoucherRepository;
import com.example.house_moving_service.modules.identity.repository.CustomerRepository;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.voucher.service.VoucherService;
import com.example.house_moving_service.modules.booking.service.BookingService;
import com.example.house_moving_service.modules.pricing.service.PricingService;
import com.example.house_moving_service.modules.booking.util.BookingCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@org.springframework.stereotype.Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingRouteRepository bookingRouteRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingServiceRepository bookingServiceRepository;
    private final BookingVoucherRepository bookingVoucherRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final VoucherService voucherService;
    private final ServiceLookupService serviceLookupService;
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
            // Tồn tại + còn hiệu lực, khóa dòng voucher đến hết transaction để chống dùng
            // vượt lượt
            voucher = voucherService.lockValidVoucher(request.getVoucherCode());

            // Kiểm tra tổng lượt dùng toàn hệ thống
            if (voucher.getTotalUsageLimit() != null) {
                long totalUsed = bookingVoucherRepository.countByVoucherId(voucher.getId());
                if (totalUsed >= voucher.getTotalUsageLimit()) {
                    throw new AppException(ErrorCode.ERR_VOUCHER_LIMIT_REACHED);
                }
            }

            // Kiểm tra lượt dùng của từng khách hàng
            if (voucher.getUsagePerUserLimit() != null) {
                long userUsed = bookingVoucherRepository.countByVoucherIdAndBooking_CustomerId(voucher.getId(),
                        customer.getId());
                if (userUsed >= voucher.getUsagePerUserLimit()) {
                    throw new AppException(ErrorCode.ERR_VOUCHER_LIMIT_REACHED);
                }
            }
        }

        // 3. Gọi PricingService để tính giá chính thức
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
        List<com.example.house_moving_service.modules.booking.entity.BookingService> bookingServicesToSave = new ArrayList<>();
        if (pricingResponse.getBreakdownItems() != null) {
            for (BreakdownItemDto breakdown : pricingResponse.getBreakdownItems()) {
                Service serviceProxy = serviceLookupService.getReference(breakdown.getServiceId());
                int quantity = breakdown.getQuantity() != null ? breakdown.getQuantity().intValue() : 1;

                com.example.house_moving_service.modules.booking.entity.BookingService bs = com.example.house_moving_service.modules.booking.entity.BookingService
                        .builder()
                        .booking(savedBooking)
                        .service(serviceProxy)
                        .priceApplied(breakdown.getUnitPrice()) // Snapshot đơn giá chuẩn
                        .quantity(quantity)
                        .note(breakdown.getNote())
                        .build();

                bookingServicesToSave.add(bs);
            }
        }
        List<com.example.house_moving_service.modules.booking.entity.BookingService> savedServices = bookingServiceRepository
                .saveAll(bookingServicesToSave);

        // 8. Lưu bảng 5: booking_vouchers (Nếu có áp dụng voucher thành công)
        List<BookingVoucher> savedVouchers = new ArrayList<>();
        if (pricingResponse.getAppliedVoucher() != null && pricingResponse.getAppliedVoucher().getVoucherId() != null) {
            Voucher appliedVoucherRef = voucherService.getReference(pricingResponse.getAppliedVoucher().getVoucherId());
            BookingVoucher bookingVoucher = BookingVoucher.builder()
                    .booking(savedBooking)
                    .voucher(appliedVoucherRef)
                    .discountAmount(pricingResponse.getAppliedVoucher().getDiscountAmount())
                    .build();

            BookingVoucher savedBookingVoucher = bookingVoucherRepository.save(bookingVoucher);
            savedVouchers.add(savedBookingVoucher);

            // Cập nhật used_count của Voucher
            if (voucher != null) {
                voucherService.increaseUsage(voucher);
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
        List<BookingStatusHistory> savedHistories = savedHistory != null ? List.of(savedHistory)
                : Collections.emptyList();

        log.info("Successfully created booking code: {} across all 6 tables", savedBooking.getBookingCode());

        return bookingMapper.toDetailResponse(
                savedBooking,
                savedRoute,
                savedItems,
                savedServices,
                savedVouchers,
                savedHistories);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDetailResponse getBookingById(Integer id, Long currentUserId) {
        log.info("Fetching booking id: {} for user: {}", id, currentUserId);

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, id));

        // 1. Kiểm tra quyền sở hữu (chống tấn công IDOR)
        if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.ERR_BOOKING_FORBIDDEN);
        }

        // 2. Nạp dữ liệu các bảng con tường minh để tránh LazyInitializationException &
        // MultipleBagFetchException
        BookingRoute route = bookingRouteRepository.findByBookingId(id).orElse(null);
        List<BookingItem> items = bookingItemRepository.findAllByBookingId(id);
        List<com.example.house_moving_service.modules.booking.entity.BookingService> services = bookingServiceRepository
                .findAllByBookingId(id);
        List<BookingVoucher> vouchers = bookingVoucherRepository.findAllByBookingId(id);
        List<BookingStatusHistory> histories = bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(id);

        return bookingMapper.toDetailResponse(
                booking,
                route,
                items,
                services,
                vouchers,
                histories);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> getCustomerBookings(Long currentUserId, BookingStatus status,
            Pageable pageable) {
        log.info("Fetching customer bookings for user: {}, status: {}, page: {}", currentUserId, status,
                pageable.getPageNumber());

        Page<Booking> bookingPage;
        if (status != null) {
            bookingPage = bookingRepository.findAllByCustomerIdAndStatus(currentUserId, status, pageable);
        } else {
            bookingPage = bookingRepository.findAllByCustomerId(currentUserId, pageable);
        }

        List<BookingSummaryResponse> summaryList = bookingPage.getContent().stream()
                .map(bookingMapper::toSummaryResponse)
                .toList();

        return PageResponse.<BookingSummaryResponse>builder()
                .pageNo(bookingPage.getNumber() + 1)
                .pageSize(bookingPage.getSize())
                .totalPages(bookingPage.getTotalPages())
                .totalElements(bookingPage.getTotalElements())
                .last(bookingPage.isLast())
                .items(summaryList)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> getCustomerBookings(Long currentUserId, Pageable pageable) {
        return getCustomerBookings(currentUserId, null, pageable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse cancelBooking(Integer id, CancelBookingRequest request, Long currentUserId) {
        log.info("Processing cancelBooking for id: {} by user: {}", id, currentUserId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_UNAUTHORIZED));

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, id));

        // 1. Kiểm tra quyền sở hữu (chống tấn công IDOR)
        if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.ERR_BOOKING_FORBIDDEN);
        }

        // 2. Kiểm tra trạng thái: Chỉ cho phép hủy khi đang ở PENDING_SURVEY
        if (booking.getStatus() != BookingStatus.PENDING_SURVEY) {
            throw new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS);
        }

        // 3. Cập nhật trạng thái Booking sang CANCELLED
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        Booking updatedBooking = bookingRepository.save(booking);

        // 4. Ghi lịch sử chuyển đổi trạng thái vào booking_status_history
        BookingStatusHistory history = BookingStatusHistory.builder()
                .booking(updatedBooking)
                .oldStatus(oldStatus)
                .newStatus(BookingStatus.CANCELLED)
                .changedBy(currentUser)
                .note(request != null ? request.getReason() : "Khách hàng hủy đơn")
                .build();
        bookingStatusHistoryRepository.save(history);

        // 5. Hoàn trả lượt dùng voucher (nếu đơn có áp dụng voucher)
        List<BookingVoucher> bookingVouchers = bookingVoucherRepository.findAllByBookingId(id);
        if (bookingVouchers != null && !bookingVouchers.isEmpty()) {
            for (BookingVoucher bv : bookingVouchers) {
                if (bv.getVoucher() != null) {
                    voucherService.releaseUsage(bv.getVoucher().getId());
                }
            }
        }

        // 6. Nạp đầy đủ các quan hệ để trả về BookingDetailResponse chuẩn
        BookingRoute route = bookingRouteRepository.findByBookingId(id).orElse(null);
        List<BookingItem> items = bookingItemRepository.findAllByBookingId(id);
        List<com.example.house_moving_service.modules.booking.entity.BookingService> services = bookingServiceRepository
                .findAllByBookingId(id);
        List<BookingStatusHistory> histories = bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(id);

        log.info("Successfully cancelled booking code: {}", updatedBooking.getBookingCode());

        return bookingMapper.toDetailResponse(
                updatedBooking,
                route,
                items,
                services,
                bookingVouchers != null ? bookingVouchers : Collections.emptyList(),
                histories);
    }
}
