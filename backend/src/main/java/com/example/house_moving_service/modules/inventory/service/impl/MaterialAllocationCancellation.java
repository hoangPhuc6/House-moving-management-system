package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import com.example.house_moving_service.modules.inventory.enums.AllocationStatus;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.inventory.repository.MaterialAllocationRepository;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Hủy một dòng cấp phát: RETURN toàn bộ phần chưa hoàn, quantity_returned = quantity_allocated, status CANCELLED,
 * không tính tiền. Dùng chung cho API hủy cấp phát và listener hủy đơn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MaterialAllocationCancellation {

    // Chuyến chưa chạy (hoặc đã hủy) thì được hủy cấp phát; IN_PROGRESS / COMPLETED thì không
    static final Set<MovingScheduleStatus> CANCELLABLE = EnumSet.of(
            MovingScheduleStatus.PLANNED, MovingScheduleStatus.CONFIRMED,
            MovingScheduleStatus.RESCHEDULED, MovingScheduleStatus.CANCELLED);

    private final MaterialAllocationRepository allocationRepository;
    private final InventoryLedger inventoryLedger;

    /** Người gọi đã khóa chuyến và dòng cấp phát, và đã kiểm dòng đang ALLOCATED, chuyến thuộc CANCELLABLE. */
    @Transactional(propagation = Propagation.MANDATORY)
    public MaterialAllocation cancel(MaterialAllocation allocation, Long actorUserId, String note) {
        int toReturn = allocation.getQuantityAllocated() - allocation.getQuantityReturned();
        if (toReturn > 0) {
            inventoryLedger.post(InventoryLedger.Entry.builder()
                    .materialId(allocation.getMaterial().getId())
                    .type(InventoryTransactionType.RETURN)
                    .quantity(toReturn)
                    .quantityChange(toReturn)
                    .allocation(allocation)
                    .note(note)
                    .actorUserId(actorUserId)
                    .build());
        }
        allocation.setQuantityReturned(allocation.getQuantityAllocated());
        allocation.setStatus(AllocationStatus.CANCELLED);
        MaterialAllocation saved = allocationRepository.save(allocation);

        log.info("Allocation id: {} cancelled, returned {} to stock (actor={})", allocation.getId(), toReturn, actorUserId);
        return saved;
    }
}
