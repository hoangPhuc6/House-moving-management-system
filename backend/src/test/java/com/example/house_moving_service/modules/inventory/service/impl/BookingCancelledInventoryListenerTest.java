package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.modules.booking.event.BookingCancelledEvent;
import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import com.example.house_moving_service.modules.inventory.enums.AllocationStatus;
import com.example.house_moving_service.modules.inventory.repository.MaterialAllocationRepository;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSnapshot;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.service.MovingScheduleLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCancelledInventoryListenerTest {

    private static final int BOOKING_ID = 7;
    private static final long ACTOR_ID = 3L;

    @Mock
    private MaterialAllocationRepository allocationRepository;
    @Mock
    private MovingScheduleLookupService movingScheduleLookupService;
    @Mock
    private MaterialAllocationCancellation allocationCancellation;

    private BookingCancelledInventoryListener listener;

    @BeforeEach
    void setUp() {
        listener = new BookingCancelledInventoryListener(allocationRepository, movingScheduleLookupService,
                allocationCancellation);
    }

    private void givenSchedule(int scheduleId, MovingScheduleStatus status) {
        when(movingScheduleLookupService.lockForUpdate(scheduleId)).thenReturn(MovingScheduleSnapshot.builder()
                .id(scheduleId).bookingId(BOOKING_ID).status(status).build());
    }

    @Test
    @DisplayName("Hủy dòng ALLOCATED của chuyến chưa chạy, bỏ qua chuyến IN_PROGRESS / COMPLETED")
    void cancelsOnlyNotStartedSchedules() {
        when(allocationRepository.findScheduleIdsByBookingIdAndStatus(BOOKING_ID, AllocationStatus.ALLOCATED))
                .thenReturn(List.of(50, 51, 52));
        givenSchedule(50, MovingScheduleStatus.CONFIRMED);
        givenSchedule(51, MovingScheduleStatus.IN_PROGRESS);
        givenSchedule(52, MovingScheduleStatus.COMPLETED);
        MaterialAllocation a1 = MaterialAllocation.builder().id(1).build();
        MaterialAllocation a2 = MaterialAllocation.builder().id(2).build();
        when(allocationRepository.findAllByScheduleIdAndStatusForUpdate(50, AllocationStatus.ALLOCATED))
                .thenReturn(List.of(a1, a2));

        listener.onBookingCancelled(new BookingCancelledEvent(BOOKING_ID, ACTOR_ID, " khách đổi ý "));

        verify(allocationCancellation).cancel(a1, ACTOR_ID, "Đơn hàng bị hủy: khách đổi ý");
        verify(allocationCancellation).cancel(a2, ACTOR_ID, "Đơn hàng bị hủy: khách đổi ý");
        verify(allocationRepository, never()).findAllByScheduleIdAndStatusForUpdate(51, AllocationStatus.ALLOCATED);
        verify(allocationRepository, never()).findAllByScheduleIdAndStatusForUpdate(52, AllocationStatus.ALLOCATED);
    }

    @Test
    @DisplayName("Chuyến IN_PROGRESS: giữ nguyên, không hoàn kho")
    void skipsInProgress() {
        when(allocationRepository.findScheduleIdsByBookingIdAndStatus(BOOKING_ID, AllocationStatus.ALLOCATED))
                .thenReturn(List.of(51));
        givenSchedule(51, MovingScheduleStatus.IN_PROGRESS);

        listener.onBookingCancelled(new BookingCancelledEvent(BOOKING_ID, ACTOR_ID, null));

        verify(allocationRepository, never()).findAllByScheduleIdAndStatusForUpdate(anyInt(), any());
        verifyNoInteractions(allocationCancellation);
    }

    @Test
    @DisplayName("Chuyến đã bị listener lịch vận chuyển hủy trước (CANCELLED) vẫn được hoàn kho")
    void cancelledScheduleStillReturned() {
        when(allocationRepository.findScheduleIdsByBookingIdAndStatus(BOOKING_ID, AllocationStatus.ALLOCATED))
                .thenReturn(List.of(50));
        givenSchedule(50, MovingScheduleStatus.CANCELLED);
        MaterialAllocation a1 = MaterialAllocation.builder().id(1).build();
        when(allocationRepository.findAllByScheduleIdAndStatusForUpdate(50, AllocationStatus.ALLOCATED))
                .thenReturn(List.of(a1));

        listener.onBookingCancelled(new BookingCancelledEvent(BOOKING_ID, ACTOR_ID, "  "));

        verify(allocationCancellation).cancel(a1, ACTOR_ID, "Đơn hàng bị hủy");
    }
}
