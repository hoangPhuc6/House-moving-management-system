package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.entity.Incident;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.helper.SurchargePricingResolver;
import com.example.house_moving_service.modules.scheduling.repository.BookingSurchargeRepository;
import com.example.house_moving_service.modules.scheduling.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/** Kiểm tra và dựng khoản phụ phí, dùng chung cho API hiện trường, điều phối và khách. Tham gia transaction của người gọi. */
@Component
@RequiredArgsConstructor
class SurchargeSupport {

    static final Set<BookingStatus> CLOSED_BOOKING_STATUSES = EnumSet.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED);
    // Đội đã đến nơi nhưng chưa bắt đầu (trường hợp chính khi khách khai sai), hoặc phát sinh giữa chừng
    static final Set<MovingScheduleStatus> SURCHARGEABLE_SCHEDULE_STATUSES =
            EnumSet.of(MovingScheduleStatus.CONFIRMED, MovingScheduleStatus.IN_PROGRESS);

    private final BookingSurchargeRepository surchargeRepository;
    private final IncidentRepository incidentRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final UserLookupService userLookupService;
    private final SurchargePricingResolver pricingResolver;

    BookingSurcharge loadForUpdate(Integer id) {
        return surchargeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURCHARGE_NOT_FOUND, id));
    }

    // Khóa dòng phụ phí và kiểm tra trạng thái hiện tại thuộc allowed
    BookingSurcharge loadForUpdate(Integer id, Set<SurchargeStatus> allowed) {
        BookingSurcharge surcharge = loadForUpdate(id);
        requireStatus(surcharge, allowed);
        return surcharge;
    }

    static void requireStatus(BookingSurcharge surcharge, Set<SurchargeStatus> allowed) {
        if (!allowed.contains(surcharge.getStatus())) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_INVALID_STATUS);
        }
    }

    static void requireBookingOpen(BookingSnapshot booking) {
        if (CLOSED_BOOKING_STATUSES.contains(booking.status())) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_BOOKING_INVALID_STATUS);
        }
    }

    static void requireScheduleOpen(MovingSchedule schedule) {
        if (!SURCHARGEABLE_SCHEDULE_STATUSES.contains(schedule.getStatus())) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_SCHEDULE_INVALID, "chuyến đang ở trạng thái " + schedule.getStatus());
        }
    }

    /**
     * Dựng khoản phụ phí mới (chưa lưu): lấy đơn giá theo loại, kiểm tra sự cố thuộc cùng đơn.
     * Người gọi đã kiểm tra đơn, chuyến và quyền.
     */
    BookingSurcharge build(Integer bookingId, MovingSchedule schedule, CreateSurchargeRequest request,
                           SurchargeStatus status, Long userId, LocalDateTime now) {
        int quantity = request.getQuantity();
        SurchargePricingResolver.Pricing pricing = pricingResolver.resolve(
                request.getSurchargeType(), quantity, request.getUnitPrice(), request.getServiceId());

        return BookingSurcharge.builder()
                .booking(bookingWorkflowService.getReference(bookingId))
                .schedule(schedule)
                .incident(resolveIncident(request.getIncidentId(), bookingId))
                .service(pricing.service())
                .surchargeType(request.getSurchargeType())
                .description(request.getDescription().trim())
                .quantity(quantity)
                .unitPrice(pricing.unitPrice())
                .amount(SurchargePricingResolver.amountOf(quantity, pricing.unitPrice()))
                .status(status)
                .requestedBy(userLookupService.getUserReference(userId))
                .requestedAt(now)
                .build();
    }

    private Incident resolveIncident(Integer incidentId, Integer bookingId) {
        if (incidentId == null) {
            return null;
        }
        return incidentRepository.findById(incidentId)
                .filter(i -> i.getBooking().getId().equals(bookingId))
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID,
                        "sự cố #" + incidentId + " không tồn tại hoặc không thuộc đơn hàng"));
    }
}
