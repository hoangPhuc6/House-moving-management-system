package com.example.house_moving_service.modules.voucher.enums;

public enum DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT;

    public static DiscountType fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if ("PERCENT".equals(normalized) || "PERCENTAGE".equals(normalized)) {
            return PERCENTAGE;
        }
        if ("FIXED".equals(normalized) || "FIXED_AMOUNT".equals(normalized)) {
            return FIXED_AMOUNT;
        }
        return DiscountType.valueOf(normalized);
    }
}

