package com.example.house_moving_service.modules.pricing.engine;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.pricing.dto.PricingInput;
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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lõi tính giá thuần: nạp dịch vụ, nhân đơn giá, cộng theo nhóm phí (service_types.fee_category), áp voucher.
 * Không kiểm tra lịch, không gọi bản đồ — bên gọi tự chuẩn bị PricingInput.
 */
@Component
@RequiredArgsConstructor
public class PricingEngine {

    private final ServiceLookupService serviceLookupService;
    private final VoucherService voucherService;

    public PricingCalculationResponse calculate(PricingInput input) {
        List<ServiceLine> lines = input.services();
        if (lines == null || lines.isEmpty()) {
            throw new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Danh sách dịch vụ không được để trống");
        }

        Map<Integer, Service> serviceMap = loadActiveServices(lines);

        // Tính từng dòng và cộng dồn theo nhóm phí (BR-PRICE-01)
        Map<FeeCategory, BigDecimal> categoryTotals = new EnumMap<>(FeeCategory.class);
        for (FeeCategory category : FeeCategory.values()) {
            categoryTotals.put(category, BigDecimal.ZERO);
        }
        List<BreakdownItemDto> breakdownItems = new ArrayList<>();

        for (ServiceLine line : lines) {
            if (line.quantity() < 1) {
                throw new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Số lượng dịch vụ phải lớn hơn 0");
            }
            Service s = serviceMap.get(line.serviceId());
            BigDecimal unitPrice = s.getBasePrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal itemTotalPrice = unitPrice.multiply(BigDecimal.valueOf(line.quantity())).setScale(2, RoundingMode.HALF_UP);

            categoryTotals.merge(s.getServiceType().getFeeCategory(), itemTotalPrice, BigDecimal::add);
            breakdownItems.add(BreakdownItemDto.builder()
                    .serviceId(s.getId())
                    .serviceTypeId(s.getServiceType().getId())
                    .serviceTypeName(s.getServiceType().getName())
                    .serviceName(s.getName())
                    .quantity(line.quantity())
                    .unitPrice(unitPrice)
                    .totalPrice(itemTotalPrice)
                    .build());
        }

        CategoryFeesDto categoryFees = CategoryFeesDto.builder()
                .baseTransportFee(scale(categoryTotals.get(FeeCategory.TRANSPORT)))
                .laborFee(scale(categoryTotals.get(FeeCategory.LABOR)))
                .technicalFee(scale(categoryTotals.get(FeeCategory.TECHNICAL)))
                .materialFee(scale(categoryTotals.get(FeeCategory.MATERIAL)))
                .terrainSurchargeFee(scale(categoryTotals.get(FeeCategory.TERRAIN)))
                .build();
        BigDecimal subtotal = scale(categoryTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));

        // Khuyến mãi (chỉ đọc)
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

    // Nạp dịch vụ bằng 1 truy vấn; thiếu → ERR_SERVICE_NOT_FOUND, dịch vụ hoặc loại đã tắt → ERR_SERVICE_INACTIVE
    private Map<Integer, Service> loadActiveServices(List<ServiceLine> lines) {
        Set<Integer> requestedIds = lines.stream().map(ServiceLine::serviceId).collect(Collectors.toSet());
        Map<Integer, Service> serviceMap = serviceLookupService.findAllByIds(requestedIds).stream()
                .collect(Collectors.toMap(Service::getId, Function.identity()));

        for (Integer serviceId : requestedIds) {
            if (!serviceMap.containsKey(serviceId)) {
                throw new AppException(ErrorCode.ERR_SERVICE_NOT_FOUND, serviceId);
            }
        }
        for (Service service : serviceMap.values()) {
            if (service.getStatus() != ServiceStatus.ACTIVE || !Boolean.TRUE.equals(service.getServiceType().getIsActive())) {
                throw new AppException(ErrorCode.ERR_SERVICE_INACTIVE, service.getName());
            }
        }
        return serviceMap;
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

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
