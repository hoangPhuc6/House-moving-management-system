package com.example.house_moving_service.modules.scheduling.enums;

import com.example.house_moving_service.modules.catalog.enums.SystemServiceCode;
import lombok.Getter;

/**
 * Loại phụ phí và cách lấy đơn giá (xem SurchargePricingResolver):
 * SYSTEM_SERVICE: tự lấy theo mã dịch vụ hệ thống, không gửi serviceId / unitPrice;
 * CATALOG_SERVICE: bắt buộc serviceId (dịch vụ ACTIVE nhóm LABOR / TECHNICAL / MATERIAL), không gửi unitPrice;
 * MANUAL: bắt buộc nhập tay unitPrice, không gửi serviceId.
 */
@Getter
public enum SurchargeType {
    EXTRA_FLOOR(PricingMode.SYSTEM_SERVICE, SystemServiceCode.SURCHARGE_STAIRS),
    NO_ELEVATOR(PricingMode.SYSTEM_SERVICE, SystemServiceCode.SURCHARGE_STAIRS),
    LONG_CARRY(PricingMode.SYSTEM_SERVICE, SystemServiceCode.SURCHARGE_DEEP_ALLEY),
    EXTRA_DISTANCE(PricingMode.SYSTEM_SERVICE, SystemServiceCode.SURCHARGE_EXTRA_KM),
    EXTRA_ITEMS(PricingMode.CATALOG_SERVICE, null),
    DISASSEMBLY(PricingMode.CATALOG_SERVICE, null),
    PACKING_MATERIAL(PricingMode.CATALOG_SERVICE, null),
    WAITING_TIME(PricingMode.MANUAL, null),
    OTHER(PricingMode.MANUAL, null);

    public enum PricingMode {
        SYSTEM_SERVICE,
        CATALOG_SERVICE,
        MANUAL
    }

    private final PricingMode pricingMode;
    // Mã dịch vụ hệ thống, chỉ có với SYSTEM_SERVICE
    private final String systemServiceCode;

    SurchargeType(PricingMode pricingMode, String systemServiceCode) {
        this.pricingMode = pricingMode;
        this.systemServiceCode = systemServiceCode;
    }

    public boolean isManualPrice() {
        return pricingMode == PricingMode.MANUAL;
    }
}
