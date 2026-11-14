package com.example.house_moving_service.modules.scheduling.helper;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.scheduling.config.SurchargeProperties;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Xác định đơn giá của khoản phụ phí theo loại (SurchargeType#getPricingMode), giống cách PricingEngine lấy giá khi báo giá:
 * loại có trong danh mục thì unitPrice = services.base_price tại thời điểm tạo; WAITING_TIME / OTHER thì nhập tay.
 */
@Component
@RequiredArgsConstructor
public class SurchargePricingResolver {

    // Nhóm phí của dịch vụ được chọn cho EXTRA_ITEMS / DISASSEMBLY / PACKING_MATERIAL
    static final Set<FeeCategory> CATALOG_FEE_CATEGORIES = EnumSet.of(FeeCategory.LABOR, FeeCategory.TECHNICAL, FeeCategory.MATERIAL);
    private static final String SYSTEM_SERVICE_PREFIX = "SURCHARGE_";

    private final ServiceLookupService serviceLookupService;
    private final SurchargeProperties surchargeProperties;

    /** Đơn giá đã chốt kèm dịch vụ tham chiếu (null với WAITING_TIME / OTHER). */
    public record Pricing(Service service, BigDecimal unitPrice) {
    }

    /**
     * Kiểm tra và lấy đơn giá lúc tạo khoản phụ phí.
     * ERR_SURCHARGE_INVALID_AMOUNT: gửi unitPrice cho loại có trong danh mục, thiếu unitPrice với loại nhập tay, vượt giới hạn.
     * ERR_SURCHARGE_REFERENCE_INVALID: serviceId thiếu / thừa / không tồn tại / không ACTIVE / sai nhóm; thiếu dịch vụ hệ thống.
     */
    public Pricing resolve(SurchargeType type, int quantity, BigDecimal unitPrice, Integer serviceId) {
        validateQuantity(quantity);
        Pricing pricing = switch (type.getPricingMode()) {
            case SYSTEM_SERVICE -> {
                rejectUnitPrice(unitPrice, type);
                if (serviceId != null) {
                    throw new AppException(ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID,
                            "loại " + type + " tự lấy dịch vụ theo mã, không gửi serviceId");
                }
                Service service = serviceLookupService.findByCode(type.getSystemServiceCode())
                        .filter(s -> s.getStatus() == ServiceStatus.ACTIVE)
                        .orElseThrow(() -> new AppException(ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID,
                                "dịch vụ hệ thống " + type.getSystemServiceCode() + " không có hoặc đã ngừng hoạt động"));
                yield new Pricing(service, service.getBasePrice());
            }
            case CATALOG_SERVICE -> {
                rejectUnitPrice(unitPrice, type);
                Service service = requireCatalogService(serviceId, type);
                yield new Pricing(service, service.getBasePrice());
            }
            case MANUAL -> {
                if (serviceId != null) {
                    throw new AppException(ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID,
                            "loại " + type + " không có trong danh mục, không gửi serviceId");
                }
                if (unitPrice == null) {
                    throw new AppException(ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT, "loại " + type + " bắt buộc nhập đơn giá");
                }
                yield new Pricing(null, unitPrice);
            }
        };
        validateUnitPrice(pricing.unitPrice());
        return pricing;
    }

    /**
     * Đơn giá khi điều phối viên chốt đề xuất (bước send): loại nhập tay được sửa đơn giá, loại có trong danh mục thì
     * giữ đơn giá đã chụp (gửi newUnitPrice → ERR_SURCHARGE_INVALID_AMOUNT).
     */
    public BigDecimal resolveOnSend(SurchargeType type, BigDecimal currentUnitPrice, BigDecimal newUnitPrice) {
        if (newUnitPrice == null) {
            return currentUnitPrice;
        }
        if (!type.isManualPrice()) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT,
                    "đơn giá của loại " + type + " lấy theo danh mục, chỉ được sửa số lượng");
        }
        validateUnitPrice(newUnitPrice);
        return newUnitPrice;
    }

    public void validateQuantity(int quantity) {
        if (quantity <= 0 || quantity > surchargeProperties.getMaxQuantity()) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT,
                    "số lượng phải từ 1 đến " + surchargeProperties.getMaxQuantity());
        }
    }

    /** amount = quantity × unitPrice, làm tròn 2 chữ số. */
    public static BigDecimal amountOf(int quantity, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT, "đơn giá phải lớn hơn 0");
        }
        if (unitPrice.compareTo(surchargeProperties.getMaxUnitPrice()) > 0) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT,
                    "đơn giá vượt mức tối đa " + surchargeProperties.getMaxUnitPrice().toPlainString() + "đ");
        }
        if (unitPrice.stripTrailingZeros().scale() > 2) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT, "đơn giá tối đa 2 chữ số thập phân");
        }
    }

    private static void rejectUnitPrice(BigDecimal unitPrice, SurchargeType type) {
        if (unitPrice != null) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT,
                    "đơn giá của loại " + type + " lấy theo danh mục dịch vụ, không được nhập tay");
        }
    }

    private Service requireCatalogService(Integer serviceId, SurchargeType type) {
        if (serviceId == null) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID, "loại " + type + " bắt buộc chọn dịch vụ (serviceId)");
        }
        List<Service> found = serviceLookupService.findAllByIds(List.of(serviceId));
        if (found.isEmpty()) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID, "dịch vụ #" + serviceId + " không tồn tại");
        }
        Service service = found.get(0);
        if (service.getStatus() != ServiceStatus.ACTIVE
                || service.getServiceType() == null
                || !Boolean.TRUE.equals(service.getServiceType().getIsActive())) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID, "dịch vụ #" + serviceId + " đã ngừng hoạt động");
        }
        boolean systemService = service.getCode() != null && service.getCode().startsWith(SYSTEM_SERVICE_PREFIX);
        if (systemService || !CATALOG_FEE_CATEGORIES.contains(service.getServiceType().getFeeCategory())) {
            throw new AppException(ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID,
                    "dịch vụ #" + serviceId + " không thuộc nhóm LABOR / TECHNICAL / MATERIAL dùng cho loại " + type);
        }
        return service;
    }
}
