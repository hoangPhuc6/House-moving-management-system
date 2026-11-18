package com.example.house_moving_service.modules.inventory.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.inventory.dto.request.AdjustInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.DamageInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.InventoryTransactionFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.request.MaterialHistoryFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.response.InventoryTransactionResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialHistoryResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Thao tác kho không gắn chuyến (nhập kho, xuất hủy, kiểm kê) và tra cứu tồn / lịch sử, dành cho điều phối / admin. */
public interface InventoryService {

    /**
     * Nhập kho: mỗi món một giao dịch IMPORT (+quantity, lưu unit_cost), cả phiếu trong một transaction.
     * Trả giao dịch theo materialId tăng dần (thứ tự khóa).
     */
    List<InventoryTransactionResponse> importStock(ImportInventoryRequest request, Long actorUserId);

    /** Xuất hủy hàng hư ngay trong kho: DAMAGED với quantity_change = −quantity, không vượt tồn, bắt buộc lý do. */
    InventoryTransactionResponse writeOffDamaged(DamageInventoryRequest request, Long actorUserId);

    /**
     * Kiểm kê: chênh lệch = actualQuantity − quantity_on_hand, ghi ADJUST với quantity = |chênh lệch|,
     * quantity_change = chênh lệch, bắt buộc lý do. Không lệch → ERR_INVENTORY_ADJUST_NO_CHANGE.
     */
    InventoryTransactionResponse adjust(AdjustInventoryRequest request, Long actorUserId);

    /** Vật tư đang hoạt động có tồn < tồn tối thiểu, thiếu nhiều nhất trước. */
    List<MaterialResponse> getLowStock();

    /**
     * Lịch sử giao dịch của một vật tư: thông tin + tồn hiện tại, tổng quantity theo loại trong khoảng lọc, trang giao
     * dịch (không truyền sort thì mới nhất trước). Không có vật tư → ERR_MATERIAL_NOT_FOUND.
     */
    MaterialHistoryResponse getMaterialHistory(Integer materialId, MaterialHistoryFilterRequest filter, Pageable pageable);

    /** Tra cứu giao dịch toàn kho; không truyền sort thì mới nhất trước (created_at desc, id desc). */
    PageResponse<InventoryTransactionResponse> searchTransactions(InventoryTransactionFilterRequest filter,
                                                                  Pageable pageable);
}
