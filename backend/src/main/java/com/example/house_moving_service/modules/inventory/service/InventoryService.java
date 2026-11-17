package com.example.house_moving_service.modules.inventory.service;

import com.example.house_moving_service.modules.inventory.dto.request.DamageInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.response.InventoryTransactionResponse;

import java.util.List;

/** Thao tác kho không gắn chuyến (nhập kho, xuất hủy, kiểm kê) dành cho điều phối / admin. */
public interface InventoryService {

    /**
     * Nhập kho: mỗi món một giao dịch IMPORT (+quantity, lưu unit_cost), cả phiếu trong một transaction.
     * Trả giao dịch theo materialId tăng dần (thứ tự khóa).
     */
    List<InventoryTransactionResponse> importStock(ImportInventoryRequest request, Long actorUserId);

    /** Xuất hủy hàng hư ngay trong kho: DAMAGED với quantity_change = −quantity, không vượt tồn, bắt buộc lý do. */
    InventoryTransactionResponse writeOffDamaged(DamageInventoryRequest request, Long actorUserId);
}
