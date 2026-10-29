package com.example.house_moving_service.modules.quotation.service.impl;

import com.example.house_moving_service.modules.pricing.config.PricingProperties;
import com.example.house_moving_service.modules.quotation.dto.request.CreateQuoteRequest;
import com.example.house_moving_service.modules.pricing.dto.request.LocationDto;
import com.example.house_moving_service.modules.pricing.dto.request.PricingCalculationRequest;
import com.example.house_moving_service.modules.quotation.dto.request.RejectQuotationRequest;
import com.example.house_moving_service.modules.pricing.dto.request.RouteDto;
import com.example.house_moving_service.modules.pricing.dto.request.ScheduleDto;
import com.example.house_moving_service.modules.pricing.dto.response.BreakdownItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationResponse;
import com.example.house_moving_service.modules.quotation.entity.Quotation;
import com.example.house_moving_service.modules.quotation.entity.QuotationItem;
import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import com.example.house_moving_service.modules.quotation.mapper.QuotationMapper;
import com.example.house_moving_service.modules.quotation.repository.QuotationRepository;
import com.example.house_moving_service.modules.pricing.service.PricingService;
import com.example.house_moving_service.modules.quotation.service.QuoteService;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.booking.dto.request.BookingItemRequest;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingItem;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.entity.BookingVoucher;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingItemRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRouteRepository;
import com.example.house_moving_service.modules.booking.repository.BookingServiceRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.repository.BookingVoucherRepository;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.voucher.service.VoucherService;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {

    /**
     * Trạng thái đơn cho phép lập báo giá. Khi module khảo sát hoàn thiện (chuyển đơn sang SURVEYING),
     * nên thu hẹp còn SURVEYING + QUOTED và kiểm tra booking_surveys đã COMPLETED.
     */
    private static final Set<BookingStatus> QUOTABLE_STATUSES = EnumSet.of(
            BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.SURVEYING, BookingStatus.QUOTED);

    private static final Set<ErrorCode> VOUCHER_ERRORS =
            EnumSet.of(ErrorCode.ERR_VOUCHER_NOT_FOUND, ErrorCode.ERR_VOUCHER_EXPIRED, ErrorCode.ERR_VOUCHER_MIN_VALUE);

    private final BookingRepository bookingRepository;
    private final BookingRouteRepository bookingRouteRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingServiceRepository bookingServiceRepository;
    private final BookingVoucherRepository bookingVoucherRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final QuotationRepository quotationRepository;
    private final UserRepository userRepository;
    private final VoucherService voucherService;
    private final PricingService pricingService;
    private final ServiceLookupService serviceLookupService;
    private final PricingProperties pricingProperties;
    private final QuotationMapper quotationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationResponse createQuote(Integer bookingId, CreateQuoteRequest request, Long staffUserId) {
        log.info("Management creating quote for booking id: {} by staff user: {}", bookingId, staffUserId);

        User staffUser = userRepository.findById(staffUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_UNAUTHORIZED));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId));

        // 1. Chỉ cho phép báo giá khi đơn chưa được khách duyệt (hoặc đang báo giá lại)
        if (!QUOTABLE_STATUSES.contains(booking.getStatus())) {
            throw new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS);
        }

        BookingRoute route = bookingRouteRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Đơn hàng chưa có thông tin lộ trình"));

        // 2. Dựng lại request tính giá từ dữ liệu đơn + kết quả khảo sát
        BigDecimal distanceKm = request.getActualDistanceKm() != null
                ? request.getActualDistanceKm()
                : route.getEstimatedDistanceKm();

        RouteDto routeDto = RouteDto.builder()
                .pickup(LocationDto.builder()
                        .address(route.getPickupAddress())
                        .lat(route.getPickupLat())
                        .lng(route.getPickupLng())
                        .propertyType(route.getPickupPropertyType())
                        .build())
                .dropoff(LocationDto.builder()
                        .address(route.getDropoffAddress())
                        .lat(route.getDropoffLat())
                        .lng(route.getDropoffLng())
                        .propertyType(route.getDropoffPropertyType())
                        .build())
                .manualDistanceKm(distanceKm)
                .build();

        ScheduleDto schedule = ScheduleDto.builder()
                .desiredStartAt(booking.getDesiredStartAt())
                .desiredEndAt(booking.getDesiredEndAt())
                .build();

        // 3. Xác định voucher áp dụng: null = giữ voucher hiện tại, chuỗi rỗng = gỡ voucher, mã khác = đổi voucher
        List<BookingVoucher> currentVouchers = bookingVoucherRepository.findAllByBookingId(bookingId);
        BookingVoucher keptVoucher = currentVouchers.isEmpty() ? null : currentVouchers.get(0);
        BookingVoucher removedVoucher = null;
        Voucher newVoucher = null;
        String voucherChangeNote = null;

        if (request.getVoucherCode() != null) {
            String requestedCode = request.getVoucherCode().trim();
            boolean sameAsCurrent = keptVoucher != null
                    && keptVoucher.getVoucher().getCode().equalsIgnoreCase(requestedCode);
            if (!sameAsCurrent) {
                if (!requestedCode.isEmpty()) {
                    newVoucher = lockAvailableVoucher(requestedCode, booking.getCustomer().getId());
                    voucherChangeNote = "Áp dụng voucher " + newVoucher.getCode();
                } else if (keptVoucher != null) {
                    voucherChangeNote = "Gỡ voucher " + keptVoucher.getVoucher().getCode();
                }
                removedVoucher = keptVoucher;
                keptVoucher = null;
            }
        }

        String appliedCode = newVoucher != null ? newVoucher.getCode()
                : keptVoucher != null ? keptVoucher.getVoucher().getCode() : null;

        PricingCalculationRequest pricingRequest = PricingCalculationRequest.builder()
                .schedule(schedule)
                .route(routeDto)
                .serviceItems(request.getServiceItems())
                .voucherCode(appliedCode)
                .build();

        // 4. Tính giá chính thức. Nếu voucher khách đã áp dụng không còn hợp lệ với giá mới
        // (hết hạn, không đạt giá trị tối thiểu...) thì gỡ voucher và tính lại không giảm giá.
        // Voucher mới do nhân viên nhập mà không hợp lệ thì báo lỗi luôn, không tự gỡ.
        PricingCalculationResponse pricingResponse;
        try {
            pricingResponse = pricingService.calculatePrice(pricingRequest, staffUserId);
        } catch (AppException ex) {
            if (keptVoucher == null || !VOUCHER_ERRORS.contains(ex.getErrorCode())) {
                throw ex;
            }
            voucherChangeNote = "Gỡ voucher: " + ex.getFormattedMessage();
            removedVoucher = keptVoucher;
            keptVoucher = null;
            pricingRequest.setVoucherCode(null);
            pricingResponse = pricingService.calculatePrice(pricingRequest, staffUserId);
        }

        // 5. Cập nhật voucher của đơn
        if (removedVoucher != null) {
            voucherService.releaseUsage(removedVoucher.getVoucher().getId());
            bookingVoucherRepository.delete(removedVoucher);
        }
        if (keptVoucher != null) {
            keptVoucher.setDiscountAmount(pricingResponse.getAppliedVoucher().getDiscountAmount());
            bookingVoucherRepository.save(keptVoucher);
        } else if (newVoucher != null) {
            bookingVoucherRepository.save(BookingVoucher.builder()
                    .booking(booking)
                    .voucher(newVoucher)
                    .discountAmount(pricingResponse.getAppliedVoucher().getDiscountAmount())
                    .build());
            voucherService.increaseUsage(newVoucher);
        }

        // 6. Cập nhật cự ly thực tế
        route.setEstimatedDistanceKm(pricingResponse.getCalculatedDistanceKm());
        bookingRouteRepository.save(route);

        // 7. Ghi đè snapshot dịch vụ theo kết quả khảo sát
        bookingServiceRepository.deleteAll(bookingServiceRepository.findAllByBookingId(bookingId));
        List<com.example.house_moving_service.modules.booking.entity.BookingService> servicesToSave = new ArrayList<>();
        for (BreakdownItemDto breakdown : pricingResponse.getBreakdownItems()) {
            servicesToSave.add(com.example.house_moving_service.modules.booking.entity.BookingService.builder()
                    .booking(booking)
                    .service(serviceLookupService.getReference(breakdown.getServiceId()))
                    .priceApplied(breakdown.getUnitPrice())
                    .quantity(toIntQuantity(breakdown.getQuantity()))
                    .note(breakdown.getNote())
                    .build());
        }
        bookingServiceRepository.saveAll(servicesToSave);

        // 8. Đồng bộ danh sách đồ đạc nếu nhân viên khảo sát có cập nhật
        if (request.getItems() != null) {
            syncBookingItems(booking, request.getItems());
        }

        // 9. Báo giá đang chờ khách phản hồi (SENT) của phiên bản trước bị thay thế -> EXPIRED
        List<Quotation> pendingQuotations = quotationRepository.findAllByBookingIdAndStatus(bookingId, QuotationStatus.SENT);
        pendingQuotations.forEach(q -> q.setStatus(QuotationStatus.EXPIRED));
        quotationRepository.saveAll(pendingQuotations);

        // 10. Tạo phiên bản báo giá mới kèm snapshot từng dòng chi phí
        int nextVersion = quotationRepository.findTopByBookingIdOrderByVersionDesc(bookingId)
                .map(q -> q.getVersion() + 1)
                .orElse(1);

        Quotation quotation = Quotation.builder()
                .booking(booking)
                .version(nextVersion)
                .amount(pricingResponse.getFinancialSummary().getSubtotal())
                .discountAmount(pricingResponse.getFinancialSummary().getDiscountAmount())
                .finalAmount(pricingResponse.getFinancialSummary().getFinalTotal())
                .status(QuotationStatus.SENT)
                .note(trimToNull(request.getNote()))
                .validUntil(LocalDateTime.now().plusDays(pricingProperties.getQuoteValidDays()))
                .createdAt(LocalDateTime.now())
                .createdBy(staffUser)
                .build();
        for (BreakdownItemDto breakdown : pricingResponse.getBreakdownItems()) {
            quotation.addItem(toQuotationItem(breakdown));
        }
        Quotation savedQuotation = quotationRepository.save(quotation);

        // 11. Chuyển trạng thái sang QUOTED. Tổng tiền của đơn chỉ được chốt khi khách chấp nhận báo giá.
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.QUOTED);
        Booking updatedBooking = bookingRepository.save(booking);

        // 12. Ghi lịch sử chuyển đổi trạng thái
        String historyNote = (request.getNote() != null && !request.getNote().trim().isEmpty())
                ? request.getNote().trim()
                : "Lập báo giá chính thức sau khảo sát";
        historyNote = "Báo giá phiên bản " + nextVersion + ": " + historyNote;
        if (voucherChangeNote != null) {
            historyNote += " (" + voucherChangeNote + ")";
        }
        saveHistory(updatedBooking, oldStatus, BookingStatus.QUOTED, staffUser, historyNote);

        log.info("Successfully quoted booking code: {} version: {} with final amount: {}",
                updatedBooking.getBookingCode(), nextVersion, savedQuotation.getFinalAmount());

        return quotationMapper.toResponse(savedQuotation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotationResponse> getQuotations(Integer bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId);
        }
        return quotationRepository.findAllByBookingIdOrderByVersionDesc(bookingId).stream()
                .map(quotationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotationResponse> getCustomerQuotations(Integer bookingId, Long currentUserId) {
        findOwnedBooking(bookingId, currentUserId);
        return quotationRepository.findAllByBookingIdOrderByVersionDesc(bookingId).stream()
                .filter(q -> q.getStatus() != QuotationStatus.DRAFT)
                .map(quotationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationResponse acceptQuotation(Integer bookingId, Integer quotationId, Long currentUserId) {
        log.info("Customer {} accepting quotation {} of booking {}", currentUserId, quotationId, bookingId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_UNAUTHORIZED));
        Booking booking = findOwnedBooking(bookingId, currentUserId);
        Quotation quotation = findRespondableQuotation(booking, quotationId);

        // 1. Chốt báo giá
        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotation.setAcceptedAt(LocalDateTime.now());
        Quotation savedQuotation = quotationRepository.save(quotation);

        // 2. Chốt tổng tiền theo báo giá & chuyển đơn sang APPROVED
        BookingStatus oldStatus = booking.getStatus();
        booking.setTotalAmount(quotation.getFinalAmount());
        booking.setDiscountAmount(quotation.getDiscountAmount());
        booking.setStatus(BookingStatus.APPROVED);
        Booking updatedBooking = bookingRepository.save(booking);

        saveHistory(updatedBooking, oldStatus, BookingStatus.APPROVED, currentUser,
                "Khách hàng chấp nhận báo giá phiên bản " + quotation.getVersion());

        log.info("Booking code: {} approved with final total: {}",
                updatedBooking.getBookingCode(), updatedBooking.getTotalAmount());

        return quotationMapper.toResponse(savedQuotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationResponse rejectQuotation(Integer bookingId, Integer quotationId,
                                             RejectQuotationRequest request, Long currentUserId) {
        log.info("Customer {} rejecting quotation {} of booking {}", currentUserId, quotationId, bookingId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_UNAUTHORIZED));
        Booking booking = findOwnedBooking(bookingId, currentUserId);
        Quotation quotation = findRespondableQuotation(booking, quotationId);

        quotation.setStatus(QuotationStatus.REJECTED);
        Quotation savedQuotation = quotationRepository.save(quotation);

        // Đơn giữ nguyên QUOTED để nhân viên lập báo giá phiên bản mới
        String reason = request != null ? trimToNull(request.getReason()) : null;
        String historyNote = "Khách hàng từ chối báo giá phiên bản " + quotation.getVersion()
                + (reason != null ? ": " + reason : "");
        saveHistory(booking, booking.getStatus(), booking.getStatus(), currentUser, historyNote);

        return quotationMapper.toResponse(savedQuotation);
    }

    /**
     * Đồng bộ đồ đạc theo ID: có ID = cập nhật món đã có, không có ID = thêm mới,
     * món hiện có không xuất hiện trong danh sách gửi lên = xóa.
     */
    private List<BookingItem> syncBookingItems(Booking booking, List<BookingItemRequest> requests) {
        Map<Integer, BookingItem> existingItems = new LinkedHashMap<>();
        for (BookingItem item : bookingItemRepository.findAllByBookingId(booking.getId())) {
            existingItems.put(item.getId(), item);
        }

        List<BookingItem> itemsToSave = new ArrayList<>();
        for (BookingItemRequest itemReq : requests) {
            BookingItem item;
            if (itemReq.getId() != null) {
                item = existingItems.remove(itemReq.getId());
                if (item == null) {
                    throw new AppException(ErrorCode.ERR_BOOKING_ITEM_NOT_FOUND, itemReq.getId());
                }
            } else {
                item = BookingItem.builder().booking(booking).build();
            }
            item.setItemName(itemReq.getItemName());
            item.setQuantity(itemReq.getQuantity() != null ? itemReq.getQuantity() : 1);
            item.setEstimatedVolumeM3(itemReq.getEstimatedVolumeM3());
            item.setIsFragile(Boolean.TRUE.equals(itemReq.getIsFragile()));
            item.setPackingRequired(Boolean.TRUE.equals(itemReq.getPackingRequired()));
            item.setDisassemblyRequired(Boolean.TRUE.equals(itemReq.getDisassemblyRequired()));
            item.setNote(itemReq.getNote());
            itemsToSave.add(item);
        }

        bookingItemRepository.deleteAll(existingItems.values());
        return bookingItemRepository.saveAll(itemsToSave);
    }

    private QuotationItem toQuotationItem(BreakdownItemDto breakdown) {
        return QuotationItem.builder()
                .itemName(breakdown.getServiceName() != null ? breakdown.getServiceName() : "Dịch vụ #" + breakdown.getServiceId())
                .quantity(toIntQuantity(breakdown.getQuantity()))
                .unitPrice(breakdown.getUnitPrice())
                .lineAmount(breakdown.getTotalPrice())
                .note(breakdown.getNote())
                .build();
    }

    /**
     * Cột quantity trong DB là số nguyên > 0: tối thiểu là 1.
     */
    private int toIntQuantity(Integer quantity) {
        if (quantity == null) {
            return 1;
        }
        return Math.max(1, quantity);
    }

    private Booking findOwnedBooking(Integer bookingId, Long currentUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId));
        // Chống IDOR: chỉ chủ đơn mới được xem/phản hồi báo giá
        if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.ERR_BOOKING_FORBIDDEN);
        }
        return booking;
    }

    /**
     * Báo giá khách được phản hồi: thuộc đơn, đơn đang QUOTED, báo giá đang SENT và còn hạn.
     */
    private Quotation findRespondableQuotation(Booking booking, Integer quotationId) {
        if (booking.getStatus() != BookingStatus.QUOTED) {
            throw new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS);
        }
        Quotation quotation = quotationRepository.findByIdAndBookingId(quotationId, booking.getId())
                .orElseThrow(() -> new AppException(ErrorCode.ERR_QUOTATION_NOT_FOUND, quotationId));
        if (quotation.getStatus() != QuotationStatus.SENT) {
            throw new AppException(ErrorCode.ERR_QUOTATION_INVALID_STATUS);
        }
        if (quotation.getValidUntil() != null && quotation.getValidUntil().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.ERR_QUOTATION_EXPIRED);
        }
        return quotation;
    }

    private void saveHistory(Booking booking, BookingStatus oldStatus, BookingStatus newStatus, User changedBy, String note) {
        bookingStatusHistoryRepository.save(BookingStatusHistory.builder()
                .booking(booking)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .note(note)
                .build());
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Khóa voucher mới (tồn tại + còn hiệu lực) và kiểm tra giới hạn lượt dùng toàn hệ thống / theo khách,
     * cùng quy tắc với lúc khách tạo đơn.
     */
    private Voucher lockAvailableVoucher(String code, Long customerId) {
        Voucher voucher = voucherService.lockValidVoucher(code);

        if (voucher.getTotalUsageLimit() != null
                && bookingVoucherRepository.countByVoucherId(voucher.getId()) >= voucher.getTotalUsageLimit()) {
            throw new AppException(ErrorCode.ERR_VOUCHER_LIMIT_REACHED);
        }
        if (voucher.getUsagePerUserLimit() != null
                && bookingVoucherRepository.countByVoucherIdAndBooking_CustomerId(voucher.getId(), customerId)
                        >= voucher.getUsagePerUserLimit()) {
            throw new AppException(ErrorCode.ERR_VOUCHER_LIMIT_REACHED);
        }
        return voucher;
    }
}
