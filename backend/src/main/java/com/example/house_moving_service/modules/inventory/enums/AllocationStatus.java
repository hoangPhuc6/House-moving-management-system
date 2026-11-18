package com.example.house_moving_service.modules.inventory.enums;

/**
 * Trạng thái dòng cấp phát vật tư cho chuyến (booking_material_allocations.status).
 * ALLOCATED ──chốt chuyến──► SETTLED (used + returned + damaged = allocated, tính tiền một lần)
 * ALLOCATED ──hủy cấp phát / đơn bị hủy──► CANCELLED (hoàn toàn bộ về kho)
 * Trạng thái cuối: SETTLED, CANCELLED.
 */
public enum AllocationStatus {
    ALLOCATED,
    SETTLED,
    CANCELLED
}
