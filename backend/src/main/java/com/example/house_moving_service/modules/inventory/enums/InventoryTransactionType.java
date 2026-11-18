package com.example.house_moving_service.modules.inventory.enums;

/**
 * Loại giao dịch kho (inventory_transactions.transaction_type) và dấu của quantity_change:
 * IMPORT +quantity · EXPORT −quantity · RETURN +quantity
 * DAMAGED 0 (hư / mất ngoài chuyến, đã trừ lúc EXPORT) hoặc −quantity (hư trong kho) · ADJUST ±quantity.
 */
public enum InventoryTransactionType {
    IMPORT,
    EXPORT,
    RETURN,
    DAMAGED,
    ADJUST;

    /** quantityChange có hợp lệ với loại giao dịch không (khớp CHECK chk_inventory_transactions_change). */
    public boolean allowsChange(int quantity, int quantityChange) {
        return switch (this) {
            case IMPORT, RETURN -> quantityChange == quantity;
            case EXPORT -> quantityChange == -quantity;
            case DAMAGED -> quantityChange == 0 || quantityChange == -quantity;
            case ADJUST -> quantityChange == quantity || quantityChange == -quantity;
        };
    }

    /** EXPORT / RETURN bắt buộc gắn dòng cấp phát (chk_inventory_transactions_allocation). */
    public boolean requiresAllocation() {
        return this == EXPORT || this == RETURN;
    }
}
