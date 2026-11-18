package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.modules.booking.event.BookingCancelledEvent;
import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import com.example.house_moving_service.modules.inventory.enums.AllocationStatus;
import com.example.house_moving_service.modules.inventory.repository.MaterialAllocationRepository;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSnapshot;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.service.MovingScheduleLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đơn bị hủy thì hoàn kho mọi dòng ALLOCATED của các chuyến chưa chạy (giống hủy cấp phát, người thao tác là người hủy
 * đơn). Chuyến IN_PROGRESS giữ nguyên (đang chạy dở, điều phối xử lý tay); chuyến COMPLETED chờ chốt bình thường.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCancelledInventoryListener {

    private final MaterialAllocationRepository allocationRepository;
    private final MovingScheduleLookupService movingScheduleLookupService;
    private final MaterialAllocationCancellation allocationCancellation;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onBookingCancelled(BookingCancelledEvent event) {
        String note = event.reason() != null && !event.reason().isBlank()
                ? "Đơn hàng bị hủy: " + event.reason().trim()
                : "Đơn hàng bị hủy";

        for (Integer scheduleId : allocationRepository.findScheduleIdsByBookingIdAndStatus(
                event.bookingId(), AllocationStatus.ALLOCATED)) {
            // Thứ tự khóa: chuyến → dòng cấp phát → vật tư. Khóa rồi mới kiểm trạng thái: giám sát viên có thể vừa
            // bấm bắt đầu chuyến đúng lúc đơn bị hủy
            MovingScheduleSnapshot schedule = movingScheduleLookupService.lockForUpdate(scheduleId);
            if (!MaterialAllocationCancellation.CANCELLABLE.contains(schedule.status())) {
                if (schedule.status() == MovingScheduleStatus.IN_PROGRESS) {
                    log.warn("Booking id: {} cancelled while moving schedule id: {} is IN_PROGRESS, "
                            + "material allocations left unchanged", event.bookingId(), scheduleId);
                }
                continue;
            }
            for (MaterialAllocation allocation : allocationRepository
                    .findAllByScheduleIdAndStatusForUpdate(scheduleId, AllocationStatus.ALLOCATED)) {
                allocationCancellation.cancel(allocation, event.actorUserId(), note);
            }
        }
    }
}
