package com.example.house_moving_service.modules.booking.service.impl;

import com.example.house_moving_service.modules.booking.dto.request.BookingManagementFilterRequest;
import com.example.house_moving_service.modules.booking.dto.request.CancelBookingRequest;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingDetailResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSummaryResponse;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingItem;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.entity.BookingVoucher;
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
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.voucher.repository.VoucherRepository;
import com.example.house_moving_service.modules.booking.repository.specification.BookingSpecification;
import com.example.house_moving_service.modules.booking.service.BookingManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class BookingManagementServiceImpl implements BookingManagementService {

    private final BookingRepository bookingRepository;
    private final BookingRouteRepository bookingRouteRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingServiceRepository bookingServiceRepository;
    private final BookingVoucherRepository bookingVoucherRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final UserRepository userRepository;
    private final VoucherRepository voucherRepository;
    private final BookingMapper bookingMapper;

    public BookingManagementServiceImpl(
            BookingRepository bookingRepository,
            BookingRouteRepository bookingRouteRepository,
            BookingItemRepository bookingItemRepository,
            BookingServiceRepository bookingServiceRepository,
            BookingVoucherRepository bookingVoucherRepository,
            BookingStatusHistoryRepository bookingStatusHistoryRepository,
            UserRepository userRepository,
            VoucherRepository voucherRepository,
            BookingMapper bookingMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingRouteRepository = bookingRouteRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.bookingServiceRepository = bookingServiceRepository;
        this.bookingVoucherRepository = bookingVoucherRepository;
        this.bookingStatusHistoryRepository = bookingStatusHistoryRepository;
        this.userRepository = userRepository;
        this.voucherRepository = voucherRepository;
        this.bookingMapper = bookingMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDetailResponse getBookingDetailByManagement(Integer bookingId) {
        log.info("Management fetching booking detail for id: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId));

        BookingRoute route = bookingRouteRepository.findByBookingId(bookingId).orElse(null);
        List<BookingItem> items = bookingItemRepository.findAllByBookingId(bookingId);
        List<com.example.house_moving_service.modules.booking.entity.BookingService> services = bookingServiceRepository.findAllByBookingId(bookingId);
        List<BookingVoucher> vouchers = bookingVoucherRepository.findAllByBookingId(bookingId);
        List<BookingStatusHistory> histories = bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(bookingId);

        return bookingMapper.toDetailResponse(
                booking,
                route,
                items,
                services,
                vouchers != null ? vouchers : Collections.emptyList(),
                histories
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> searchAllBookings(BookingManagementFilterRequest filter, Pageable pageable) {
        log.info("Management searching bookings with page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Specification<Booking> spec = BookingSpecification.filterBookings(filter);
        Page<Booking> bookingPage = bookingRepository.findAll(spec, pageable);

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
    @Transactional(rollbackFor = Exception.class)
    public BookingDetailResponse cancelBookingByManagement(Integer bookingId, CancelBookingRequest request, Long staffUserId) {
        log.info("Management cancelling booking id: {} by staff user: {}", bookingId, staffUserId);

        User staffUser = userRepository.findById(staffUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_UNAUTHORIZED));

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS);
        }

        // Cập nhật trạng thái Booking sang CANCELLED
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        Booking updatedBooking = bookingRepository.save(booking);

        // Ghi lịch sử chuyển đổi trạng thái vào booking_status_history
        String cancelReason = (request != null && request.getReason() != null && !request.getReason().trim().isEmpty())
                ? request.getReason().trim()
                : "Quản trị viên / Điều phối viên hủy đơn";

        BookingStatusHistory history = BookingStatusHistory.builder()
                .booking(updatedBooking)
                .oldStatus(oldStatus)
                .newStatus(BookingStatus.CANCELLED)
                .changedBy(staffUser)
                .note(cancelReason)
                .build();
        bookingStatusHistoryRepository.save(history);

        // Hoàn trả lượt dùng voucher (nếu đơn có áp dụng voucher)
        List<BookingVoucher> bookingVouchers = bookingVoucherRepository.findAllByBookingId(bookingId);
        if (bookingVouchers != null && !bookingVouchers.isEmpty()) {
            for (BookingVoucher bv : bookingVouchers) {
                if (bv.getVoucher() != null) {
                    Voucher voucher = voucherRepository.findById(bv.getVoucher().getId()).orElse(null);
                    if (voucher != null && voucher.getUsedCount() != null && voucher.getUsedCount() > 0) {
                        voucher.setUsedCount(voucher.getUsedCount() - 1);
                        voucherRepository.save(voucher);
                        log.info("Reverted usedCount for voucher: {} upon management cancellation", voucher.getCode());
                    }
                }
            }
        }

        // Nạp đầy đủ các quan hệ con để trả về BookingDetailResponse chuẩn
        BookingRoute route = bookingRouteRepository.findByBookingId(bookingId).orElse(null);
        List<BookingItem> items = bookingItemRepository.findAllByBookingId(bookingId);
        List<com.example.house_moving_service.modules.booking.entity.BookingService> services = bookingServiceRepository.findAllByBookingId(bookingId);
        List<BookingStatusHistory> histories = bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(bookingId);

        log.info("Successfully cancelled booking code: {} by management", updatedBooking.getBookingCode());

        return bookingMapper.toDetailResponse(
                updatedBooking,
                route,
                items,
                services,
                bookingVouchers != null ? bookingVouchers : Collections.emptyList(),
                histories
        );
    }
}

