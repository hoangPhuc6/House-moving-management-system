package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.modules.booking.event.BookingCancelledEvent;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đơn bị hủy thì hủy luôn các chuyến chưa bắt đầu và phân công của chúng, để xe / nhân viên không còn bị tính là bận
 * khi kiểm tra trùng lịch. Chuyến IN_PROGRESS giữ nguyên (đang chạy dở, điều phối xử lý tay).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCancelledMovingListener {

    private final MovingScheduleRepository movingScheduleRepository;
    private final MovingScheduleCancellation movingScheduleCancellation;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onBookingCancelled(BookingCancelledEvent event) {
        String reason = event.reason() != null && !event.reason().isBlank()
                ? "Đơn hàng bị hủy: " + event.reason().trim()
                : "Đơn hàng bị hủy";

        for (MovingSchedule found : movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(event.bookingId())) {
            if (!MovingScheduleCancellation.CANCELLABLE.contains(found.getStatus())) {
                if (found.getStatus() == MovingScheduleStatus.IN_PROGRESS) {
                    log.warn("Booking id: {} cancelled while moving schedule id: {} is IN_PROGRESS, left unchanged",
                            event.bookingId(), found.getId());
                }
                continue;
            }
            // Khóa rồi kiểm tra lại: giám sát viên có thể vừa bấm bắt đầu chuyến đúng lúc đơn bị hủy
            movingScheduleRepository.findByIdForUpdate(found.getId())
                    .filter(s -> MovingScheduleCancellation.CANCELLABLE.contains(s.getStatus()))
                    .ifPresent(s -> movingScheduleCancellation.cancel(s, event.actorUserId(), reason));
        }
    }
}
