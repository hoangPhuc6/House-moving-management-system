package com.example.house_moving_service.modules.scheduling.mapper;

import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.scheduling.dto.response.BookingSurchargeSummaryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeSummaryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SurchargeResponse;
import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SurchargeMapper {

    public SurchargeResponse toResponse(BookingSurcharge s) {
        return SurchargeResponse.builder()
                .id(s.getId())
                .bookingId(s.getBooking().getId())
                .bookingCode(s.getBooking().getBookingCode())
                .scheduleId(s.getSchedule() != null ? s.getSchedule().getId() : null)
                .incidentId(s.getIncident() != null ? s.getIncident().getId() : null)
                .serviceId(s.getService() != null ? s.getService().getId() : null)
                .serviceName(s.getService() != null ? s.getService().getName() : null)
                .surchargeType(s.getSurchargeType())
                .description(s.getDescription())
                .quantity(s.getQuantity())
                .unitPrice(s.getUnitPrice())
                .amount(s.getAmount())
                .status(s.getStatus())
                .requestedBy(s.getRequestedBy() != null ? s.getRequestedBy().getId() : null)
                .requestedAt(s.getRequestedAt())
                .reviewedBy(s.getReviewedBy() != null ? s.getReviewedBy().getId() : null)
                .reviewedAt(s.getReviewedAt())
                .reviewNote(s.getReviewNote())
                .sentToCustomerAt(s.getSentToCustomerAt())
                .customerRespondedAt(s.getCustomerRespondedAt())
                .customerNote(s.getCustomerNote())
                .collected(s.isCollected())
                .collectedBy(s.getCollectedBy() != null ? s.getCollectedBy().getId() : null)
                .collectedAt(s.getCollectedAt())
                .paymentId(s.getPaymentId())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    public CustomerSurchargeResponse toCustomerResponse(BookingSurcharge s) {
        return CustomerSurchargeResponse.builder()
                .id(s.getId())
                .scheduleId(s.getSchedule() != null ? s.getSchedule().getId() : null)
                .surchargeType(s.getSurchargeType())
                .serviceName(s.getService() != null ? s.getService().getName() : null)
                .description(s.getDescription())
                .quantity(s.getQuantity())
                .unitPrice(s.getUnitPrice())
                .amount(s.getAmount())
                .status(s.getStatus())
                .sentToCustomerAt(s.getSentToCustomerAt())
                .customerRespondedAt(s.getCustomerRespondedAt())
                .customerNote(s.getCustomerNote())
                .collected(s.isCollected())
                .collectedAt(s.getCollectedAt())
                .build();
    }

    /** Chỉ các khoản khách được thấy (WAITING_CUSTOMER / ACCEPTED / REJECTED_BY_CUSTOMER), giữ thứ tự truyền vào. */
    public CustomerSurchargeSummaryResponse toCustomerSummary(Collection<BookingSurcharge> all) {
        List<BookingSurcharge> visible = all.stream()
                .filter(s -> SurchargeStatus.VISIBLE_TO_CUSTOMER.contains(s.getStatus()))
                .toList();
        List<BookingSurcharge> waiting = visible.stream()
                .filter(s -> s.getStatus() == SurchargeStatus.WAITING_CUSTOMER)
                .toList();
        return CustomerSurchargeSummaryResponse.builder()
                .waitingCount(waiting.size())
                .waitingTotal(sum(waiting))
                .acceptedTotal(acceptedTotal(visible))
                .uncollectedTotal(uncollectedTotal(visible))
                .surcharges(visible.stream().map(this::toCustomerResponse).toList())
                .build();
    }

    /** Toàn bộ khoản của đơn cho điều phối (mọi trạng thái), giữ thứ tự truyền vào. */
    public BookingSurchargeSummaryResponse toBookingSummary(BookingSnapshot booking, Collection<BookingSurcharge> all) {
        return BookingSurchargeSummaryResponse.builder()
                .bookingId(booking.id())
                .bookingCode(booking.bookingCode())
                .totalsByStatus(totalsByStatus(all))
                .acceptedTotal(acceptedTotal(all))
                .uncollectedTotal(uncollectedTotal(all))
                .blockingSurchargeIds(blockingIds(all))
                .surcharges(all.stream().map(this::toResponse).toList())
                .build();
    }

    // Tổng tiền theo từng trạng thái, đủ mọi trạng thái (0 nếu không có khoản nào)
    public static Map<SurchargeStatus, BigDecimal> totalsByStatus(Collection<BookingSurcharge> surcharges) {
        Map<SurchargeStatus, BigDecimal> totals = new EnumMap<>(SurchargeStatus.class);
        for (SurchargeStatus status : SurchargeStatus.values()) {
            totals.put(status, BigDecimal.ZERO);
        }
        surcharges.forEach(s -> totals.merge(s.getStatus(), s.getAmount(), BigDecimal::add));
        return totals;
    }

    // Tổng đã được khách xác nhận (đã cộng vào bookings.total_amount)
    public static BigDecimal acceptedTotal(Collection<BookingSurcharge> surcharges) {
        return sum(surcharges.stream().filter(s -> s.getStatus() == SurchargeStatus.ACCEPTED).toList());
    }

    // Đã xác nhận nhưng người vận chuyển chưa thu tiền
    public static BigDecimal uncollectedTotal(Collection<BookingSurcharge> surcharges) {
        return sum(surcharges.stream()
                .filter(s -> s.getStatus() == SurchargeStatus.ACCEPTED && !s.isCollected())
                .toList());
    }

    // Id các khoản chặn bắt đầu chuyến
    public static List<Integer> blockingIds(Collection<BookingSurcharge> surcharges) {
        return surcharges.stream()
                .filter(s -> SurchargeStatus.BLOCKING_START.contains(s.getStatus()))
                .map(BookingSurcharge::getId)
                .sorted()
                .toList();
    }

    private static BigDecimal sum(Collection<BookingSurcharge> surcharges) {
        return surcharges.stream().map(BookingSurcharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
