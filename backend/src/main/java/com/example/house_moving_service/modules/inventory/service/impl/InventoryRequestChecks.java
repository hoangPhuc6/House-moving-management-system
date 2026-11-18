package com.example.house_moving_service.modules.inventory.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.inventory.entity.Material;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Kiểm tra dùng chung cho request nhập / xuất / hoàn kho. */
final class InventoryRequestChecks {

    private InventoryRequestChecks() {
    }

    static void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new AppException(ErrorCode.ERR_INVENTORY_INVALID_QUANTITY, "số lượng phải lớn hơn 0");
        }
    }

    /** Trùng id trong cùng request → ERR_INVALID_INPUT. */
    static void requireDistinct(Collection<Integer> ids, String label) {
        Set<Integer> seen = new HashSet<>();
        for (Integer id : ids) {
            if (!seen.add(id)) {
                throw new AppException(ErrorCode.ERR_INVALID_INPUT, label + " " + id + " bị trùng trong request");
            }
        }
    }

    static void requireActive(Material material) {
        if (!Boolean.TRUE.equals(material.getIsActive())) {
            throw new AppException(ErrorCode.ERR_MATERIAL_INACTIVE, material.getCode());
        }
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
