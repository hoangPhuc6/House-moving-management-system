package com.example.house_moving_service.modules.pricing.engine;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import com.example.house_moving_service.modules.catalog.enums.SystemServiceCode;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.pricing.config.PricingProperties;
import com.example.house_moving_service.modules.pricing.dto.PricingInput;
import com.example.house_moving_service.modules.pricing.dto.PricingInput.AccessInfo;
import com.example.house_moving_service.modules.pricing.dto.PricingInput.ServiceLine;
import com.example.house_moving_service.modules.pricing.dto.response.AppliedVoucherDto;
import com.example.house_moving_service.modules.pricing.dto.response.BreakdownItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.CategoryFeesDto;
import com.example.house_moving_service.modules.pricing.dto.response.FinancialSummaryDto;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.voucher.enums.DiscountType;
import com.example.house_moving_service.modules.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lõi tính giá thuần: nạp dịch vụ, nhân đơn giá, tự cộng phụ phí (vượt km, thang bộ, hẻm sâu),
 * cộng theo nhóm phí (service_types.fee_category), áp voucher.
 * Không kiểm tra lịch, không gọi bản đồ — bên gọi tự chuẩn bị PricingInput.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PricingEngine {

    private final ServiceLookupService serviceLookupService;
    private final VoucherService voucherService;
    private final PricingProperties pricingProperties;

    // Một khoản phụ phí hệ thống cần cộng: mã dịch vụ, số lượng, giải thích cho khách
    private record AutoSurcharge(String code, int quantity, String note) {
    }

    public PricingCalculationResponse calculate(PricingInput input) {
        List<ServiceLine> lines = input.services();
        if (lines == null || lines.isEmpty()) {
            throw new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Danh sách dịch vụ không được để trống");
        }
        for (ServiceLine line : lines) {
            if (line.quantity() < 1) {
                throw new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Số lượng dịch vụ phải lớn hơn 0");
            }
        }

        Map<Integer, Service> serviceMap = loadServices(lines);
        // T2: khách tự chọn dịch vụ phụ phí hệ thống → bỏ dòng đó, hệ thống tự tính bên dưới (tránh tính hai lần)
        List<ServiceLine> customerLines = lines.stream()
                .filter(line -> serviceMap.get(line.serviceId()).getCode() == null)
                .toList();
        ensureActive(customerLines, serviceMap);

        // Tính từng dòng và cộng dồn theo nhóm phí (BR-PRICE-01)
        Map<FeeCategory, BigDecimal> categoryTotals = new EnumMap<>(FeeCategory.class);
        for (FeeCategory category : FeeCategory.values()) {
            categoryTotals.put(category, BigDecimal.ZERO);
        }
        List<BreakdownItemDto> breakdownItems = new ArrayList<>();

        for (ServiceLine line : customerLines) {
            addLine(serviceMap.get(line.serviceId()), line.quantity(), null, false, categoryTotals, breakdownItems);
        }

        // Phụ phí tự động: dịch vụ phụ phí thiếu / đã tắt thì bỏ qua khoản đó (T4)
        BigDecimal includedKm = pricingProperties.getBaseTransportIncludedKm();
        int chargeableExtraKm = chargeableExtraKm(input.distanceKm(), includedKm);
        Map<String, Optional<Service>> systemServices = new HashMap<>();
        for (AutoSurcharge surcharge : autoSurcharges(input, includedKm, chargeableExtraKm)) {
            systemServices.computeIfAbsent(surcharge.code(), this::findActiveSystemService)
                    .ifPresent(s -> addLine(s, surcharge.quantity(), surcharge.note(), true, categoryTotals, breakdownItems));
        }

        CategoryFeesDto categoryFees = CategoryFeesDto.builder()
                .baseTransportFee(scale(categoryTotals.get(FeeCategory.TRANSPORT)))
                .laborFee(scale(categoryTotals.get(FeeCategory.LABOR)))
                .technicalFee(scale(categoryTotals.get(FeeCategory.TECHNICAL)))
                .materialFee(scale(categoryTotals.get(FeeCategory.MATERIAL)))
                .terrainSurchargeFee(scale(categoryTotals.get(FeeCategory.TERRAIN)))
                .build();
        BigDecimal subtotal = scale(categoryTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));

        // Khuyến mãi (chỉ đọc), tính trên tạm tính đã gồm phụ phí
        AppliedVoucherDto appliedVoucher = null;
        BigDecimal discountAmount = scale(BigDecimal.ZERO);
        if (input.voucherCode() != null && !input.voucherCode().trim().isEmpty()) {
            Voucher voucher = voucherService.getValidVoucher(input.voucherCode());
            discountAmount = calculateDiscount(voucher, subtotal);
            appliedVoucher = AppliedVoucherDto.builder()
                    .voucherId(voucher.getId())
                    .code(voucher.getCode())
                    .title(voucher.getTitle())
                    .discountType(voucher.getDiscountType().name())
                    .discountValue(voucher.getDiscountValue().setScale(2, RoundingMode.HALF_UP))
                    .discountAmount(discountAmount)
                    .build();
        }

        // FinalTotal = max(0, Subtotal - DiscountAmount)
        BigDecimal finalTotal = scale(subtotal.subtract(discountAmount).max(BigDecimal.ZERO));

        return PricingCalculationResponse.builder()
                .calculatedDistanceKm(input.distanceKm())
                .includedKm(includedKm)
                .chargeableExtraKm(chargeableExtraKm)
                .financialSummary(FinancialSummaryDto.builder()
                        .subtotal(subtotal)
                        .discountAmount(discountAmount)
                        .finalTotal(finalTotal)
                        .build())
                .categoryFees(categoryFees)
                .breakdownItems(breakdownItems)
                .appliedVoucher(appliedVoucher)
                .build();
    }

    private void addLine(Service s, int quantity, String note, boolean autoApplied,
                         Map<FeeCategory, BigDecimal> categoryTotals, List<BreakdownItemDto> breakdownItems) {
        BigDecimal unitPrice = scale(s.getBasePrice());
        BigDecimal totalPrice = scale(unitPrice.multiply(BigDecimal.valueOf(quantity)));

        categoryTotals.merge(s.getServiceType().getFeeCategory(), totalPrice, BigDecimal::add);
        breakdownItems.add(BreakdownItemDto.builder()
                .serviceId(s.getId())
                .serviceTypeId(s.getServiceType().getId())
                .serviceTypeName(s.getServiceType().getName())
                .serviceName(s.getName())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .totalPrice(totalPrice)
                .note(note)
                .autoApplied(autoApplied)
                .build());
    }

    // Nạp dịch vụ bằng 1 truy vấn; thiếu → ERR_SERVICE_NOT_FOUND
    private Map<Integer, Service> loadServices(List<ServiceLine> lines) {
        Set<Integer> requestedIds = lines.stream().map(ServiceLine::serviceId).collect(Collectors.toSet());
        Map<Integer, Service> serviceMap = serviceLookupService.findAllByIds(requestedIds).stream()
                .collect(Collectors.toMap(Service::getId, Function.identity()));

        for (Integer serviceId : requestedIds) {
            if (!serviceMap.containsKey(serviceId)) {
                throw new AppException(ErrorCode.ERR_SERVICE_NOT_FOUND, serviceId);
            }
        }
        return serviceMap;
    }

    // Dịch vụ khách chọn mà đã tắt (hoặc loại đã tắt) → ERR_SERVICE_INACTIVE
    private void ensureActive(List<ServiceLine> lines, Map<Integer, Service> serviceMap) {
        for (ServiceLine line : lines) {
            Service service = serviceMap.get(line.serviceId());
            if (!isActive(service)) {
                throw new AppException(ErrorCode.ERR_SERVICE_INACTIVE, service.getName());
            }
        }
    }

    private static boolean isActive(Service service) {
        return service.getStatus() == ServiceStatus.ACTIVE && Boolean.TRUE.equals(service.getServiceType().getIsActive());
    }

    private Optional<Service> findActiveSystemService(String code) {
        Optional<Service> service = serviceLookupService.findByCode(code);
        if (service.isEmpty()) {
            log.warn("System surcharge service {} not found, skipping this surcharge", code);
            return Optional.empty();
        }
        if (!isActive(service.get())) {
            log.warn("System surcharge service {} is inactive, skipping this surcharge", code);
            return Optional.empty();
        }
        return service;
    }

    // Số km vượt, làm tròn lên: 10.2 km với 10 km đã gồm → 1 km
    private static int chargeableExtraKm(BigDecimal distanceKm, BigDecimal includedKm) {
        if (distanceKm == null || distanceKm.compareTo(includedKm) <= 0) {
            return 0;
        }
        return distanceKm.subtract(includedKm).setScale(0, RoundingMode.CEILING).intValueExact();
    }

    private List<AutoSurcharge> autoSurcharges(PricingInput input, BigDecimal includedKm, int chargeableExtraKm) {
        List<AutoSurcharge> surcharges = new ArrayList<>();
        if (chargeableExtraKm > 0) {
            surcharges.add(new AutoSurcharge(SystemServiceCode.SURCHARGE_EXTRA_KM, chargeableExtraKm,
                    "Vượt %d km (%s km, đã gồm %s km)".formatted(chargeableExtraKm, plain(input.distanceKm()), plain(includedKm))));
        }
        addAccessSurcharges(surcharges, "Điểm lấy hàng", input.pickup());
        addAccessSurcharges(surcharges, "Điểm giao hàng", input.dropoff());
        return surcharges;
    }

    // Thang bộ: không thang máy, từ tầng 2 → (tầng − 1) lầu. Hẻm sâu: đi bộ vượt mốc → 1 lần. Mỗi điểm tính riêng
    private void addAccessSurcharges(List<AutoSurcharge> surcharges, String pointLabel, AccessInfo access) {
        if (access == null) {
            return;
        }
        if (!access.hasElevator() && access.floorNumber() >= 2) {
            int floors = access.floorNumber() - 1;
            surcharges.add(new AutoSurcharge(SystemServiceCode.SURCHARGE_STAIRS, floors,
                    "%s: tầng %d, không thang máy (%d tầng)".formatted(pointLabel, access.floorNumber(), floors)));
        }
        if (access.walkingDistanceMeters() > pricingProperties.getDeepAlleyMeters()) {
            surcharges.add(new AutoSurcharge(SystemServiceCode.SURCHARGE_DEEP_ALLEY, 1,
                    "%s: đi bộ %d m từ chỗ đậu xe".formatted(pointLabel, access.walkingDistanceMeters())));
        }
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal subtotal) {
        if (voucher.getMinOrderValue() != null && subtotal.compareTo(voucher.getMinOrderValue()) < 0) {
            throw new AppException(ErrorCode.ERR_VOUCHER_MIN_VALUE, voucher.getMinOrderValue().doubleValue());
        }

        BigDecimal discount;
        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = subtotal.multiply(voucher.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discount = voucher.getMaxDiscountAmount();
            }
        } else { // FIXED_AMOUNT
            discount = voucher.getDiscountValue();
        }

        // Không giảm quá subtotal
        return scale(discount.min(subtotal));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
