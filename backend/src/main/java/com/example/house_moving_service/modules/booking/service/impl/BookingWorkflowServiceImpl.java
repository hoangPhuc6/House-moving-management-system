package com.example.house_moving_service.modules.booking.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.RouteAccessInfo;
import com.example.house_moving_service.modules.booking.dto.response.BookingItemSnapshot;
import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.mapper.BookingMapper;
import com.example.house_moving_service.modules.booking.repository.BookingItemRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRouteRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.booking.util.RouteAccessNoteCodec;
import com.example.house_moving_service.modules.identity.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;


@Service
@Slf4j
@RequiredArgsConstructor
public class BookingWorkflowServiceImpl implements BookingWorkflowService {

    private final BookingRepository bookingRepository;
    private final BookingRouteRepository bookingRouteRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final BookingMapper bookingMapper;
    // Tạm dùng để lấy User reference cho changed_by, tránh gọi thẳng UserRepository của identity.
    // Đổi sang StaffLookupService#getUserReference khi task 4.2 được merge.
    private final EntityManager entityManager;

    @Override
    public Booking getReference(Integer bookingId) {
        ensureExists(bookingId);
        return bookingRepository.getReferenceById(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingSnapshot getSnapshot(Integer bookingId) {
        Booking booking = findBooking(bookingId);

        Long customerUserId = null;
        String customerName = null;
        if (booking.getCustomer() != null) {
            customerUserId = booking.getCustomer().getId();
            customerName = booking.getCustomer().getFullName();
        }

        return BookingSnapshot.builder()
                .id(booking.getId())
                .bookingCode(booking.getBookingCode())
                .status(booking.getStatus())
                .customerUserId(customerUserId)
                .customerName(customerName)
                .contactName(booking.getContactName())
                .contactPhone(booking.getContactPhone())
                .desiredStartAt(booking.getDesiredStartAt())
                .note(booking.getNote())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingItemSnapshot> getItems(Integer bookingId) {
        ensureExists(bookingId);
        return bookingItemRepository.findAllByBookingId(bookingId).stream()
                .map(item -> BookingItemSnapshot.builder()
                        .id(item.getId())
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .estimatedVolumeM3(item.getEstimatedVolumeM3())
                        .isFragile(item.getIsFragile())
                        .packingRequired(item.getPackingRequired())
                        .disassemblyRequired(item.getDisassemblyRequired())
                        .note(item.getNote())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingRouteResponse getRoute(Integer bookingId) {
        return bookingMapper.toRouteResponse(findRoute(bookingId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRouteAccess(Integer bookingId, RouteAccessInfo pickup, RouteAccessInfo dropoff) {
        BookingRoute route = findRoute(bookingId);

        if (pickup != null) {
            route.setPickupNote(RouteAccessNoteCodec.format(pickup));
        }
        if (dropoff != null) {
            route.setDropoffNote(RouteAccessNoteCodec.format(dropoff));
        }
        bookingRouteRepository.save(route);

        log.info("Updated route access for booking id: {}", bookingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Integer bookingId, Set<BookingStatus> allowedFrom, BookingStatus to, Long actorUserId, String note) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId));

        BookingStatus oldStatus = booking.getStatus();
        if (oldStatus == to) {
            return;
        }
        if (allowedFrom == null || !allowedFrom.contains(oldStatus)) {
            throw new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS);
        }

        booking.setStatus(to);
        Booking updatedBooking = bookingRepository.save(booking);

        User actor = actorUserId != null ? entityManager.getReference(User.class, actorUserId) : null;
        BookingStatusHistory history = BookingStatusHistory.builder()
                .booking(updatedBooking)
                .oldStatus(oldStatus)
                .newStatus(to)
                .changedBy(actor)
                .note(note)
                .build();
        bookingStatusHistoryRepository.save(history);

        log.info("Booking id: {} changed status {} -> {} by user: {}", bookingId, oldStatus, to, actorUserId);
    }

    private Booking findBooking(Integer bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId));
    }

    private void ensureExists(Integer bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId);
        }
    }

    private BookingRoute findRoute(Integer bookingId) {
        return bookingRouteRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId));
    }
}
