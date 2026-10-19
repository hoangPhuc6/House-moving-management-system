package com.example.house_moving_service.service.impl;

import com.example.house_moving_service.config.PricingProperties;
import com.example.house_moving_service.dto.request.pricing.PricingCalculationRequest;
import com.example.house_moving_service.dto.request.pricing.ServiceItemDto;
import com.example.house_moving_service.dto.response.pricing.AppliedVoucherDto;
import com.example.house_moving_service.dto.response.pricing.BreakdownItemDto;
import com.example.house_moving_service.dto.response.pricing.CategoryFeesDto;
import com.example.house_moving_service.dto.response.pricing.FinancialSummaryDto;
import com.example.house_moving_service.dto.response.pricing.PricingCalculationResponse;
import com.example.house_moving_service.entity.Service;
import com.example.house_moving_service.entity.Voucher;
import com.example.house_moving_service.enums.DiscountType;
import com.example.house_moving_service.enums.ServiceStatus;
import com.example.house_moving_service.exception.AppException;
import com.example.house_moving_service.exception.ErrorCode;
import com.example.house_moving_service.repository.ServiceRepository;
import com.example.house_moving_service.repository.VoucherRepository;
import com.example.house_moving_service.service.PricingService;
import com.example.house_moving_service.service.helper.RouteDistanceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PricingServiceImpl implements PricingService {

    private final PricingProperties pricingProperties;
    private final RouteDistanceHelper routeDistanceHelper;
    private final ServiceRepository serviceRepository;
    private final VoucherRepository voucherRepository;

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private enum FeeCategory {
        TRANSPORT,
        LABOR,
        TECHNICAL,
        MATERIAL,
        TERRAIN
    }

    @Override
    public PricingCalculationResponse calculatePrice(PricingCalculationRequest request) {
        return calculatePrice(request, null);
    }

    @Override
    public PricingCalculationResponse calculatePrice(PricingCalculationRequest request, Long userId) {
        log.info("Starting pricing calculation for user {} with {} items",
                userId != null ? userId : "ANONYMOUS",
                request != null && request.getServiceItems() != null ? request.getServiceItems().size() : 0);

        // 1. Validate lịch trình
        validateSchedule(request);

        // 2. Phân giải cự ly di chuyển qua RouteDistanceHelper
        BigDecimal calculatedDistanceKm = routeDistanceHelper.resolveDistance(request.getRoute());

        // 3. Batch query dữ liệu dịch vụ (1 câu SQL duy nhất) & kiểm tra hợp lệ
        List<ServiceItemDto> items = request.getServiceItems();
        if (items == null || items.isEmpty()) {
            throw new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Danh sách dịch vụ không được để trống");
        }

        Set<Integer> requestedServiceIds = items.stream()
                .map(ServiceItemDto::getServiceId)
                .collect(Collectors.toSet());

        List<Service> services = serviceRepository.findAllByIdIn(requestedServiceIds);
        Map<Integer, Service> serviceMap = services.stream()
                .collect(Collectors.toMap(Service::getId, s -> s));

        // Kiểm tra tồn tại dịch vụ
        for (Integer serviceId : requestedServiceIds) {
            if (!serviceMap.containsKey(serviceId)) {
                throw new AppException(ErrorCode.ERR_SERVICE_NOT_FOUND, serviceId);
            }
        }

        // Kiểm tra trạng thái hoạt động (chặn nếu INACTIVE)
        for (Service service : services) {
            if (service.getStatus() != ServiceStatus.ACTIVE
                    || (service.getServiceType() != null && !Boolean.TRUE.equals(service.getServiceType().getIsActive()))) {
                throw new AppException(ErrorCode.ERR_SERVICE_INACTIVE, service.getName());
            }
        }

        // 4. Tính toán 5 cấu phần chi phí (BR-PRICE-01) và snapshot breakdown
        BigDecimal baseTransportFee = BigDecimal.ZERO;
        BigDecimal laborFee = BigDecimal.ZERO;
        BigDecimal technicalFee = BigDecimal.ZERO;
        BigDecimal materialFee = BigDecimal.ZERO;
        BigDecimal terrainSurchargeFee = BigDecimal.ZERO;

        List<BreakdownItemDto> breakdownItems = new ArrayList<>();

        for (ServiceItemDto item : items) {
            Service s = serviceMap.get(item.getServiceId());
            BigDecimal unitPrice = s.getBasePrice();
            BigDecimal quantity = item.getQuantity();

            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Số lượng dịch vụ phải lớn hơn 0");
            }

            BigDecimal itemTotalPrice = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

            FeeCategory category = determineCategory(s);
            switch (category) {
                case TRANSPORT -> baseTransportFee = baseTransportFee.add(itemTotalPrice);
                case LABOR -> laborFee = laborFee.add(itemTotalPrice);
                case TECHNICAL -> technicalFee = technicalFee.add(itemTotalPrice);
                case MATERIAL -> materialFee = materialFee.add(itemTotalPrice);
                case TERRAIN -> terrainSurchargeFee = terrainSurchargeFee.add(itemTotalPrice);
            }

            breakdownItems.add(BreakdownItemDto.builder()
                    .serviceId(s.getId())
                    .serviceTypeId(s.getServiceType() != null ? s.getServiceType().getId() : null)
                    .serviceTypeName(s.getServiceType() != null ? s.getServiceType().getName() : null)
                    .serviceName(s.getName())
                    .quantity(quantity)
                    .unitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP))
                    .totalPrice(itemTotalPrice)
                    .build());
        }

        // 5. Tổng hợp Subtotal
        baseTransportFee = baseTransportFee.setScale(2, RoundingMode.HALF_UP);
        laborFee = laborFee.setScale(2, RoundingMode.HALF_UP);
        technicalFee = technicalFee.setScale(2, RoundingMode.HALF_UP);
        materialFee = materialFee.setScale(2, RoundingMode.HALF_UP);
        terrainSurchargeFee = terrainSurchargeFee.setScale(2, RoundingMode.HALF_UP);

        BigDecimal subtotal = baseTransportFee.add(laborFee)
                .add(technicalFee)
                .add(materialFee)
                .add(terrainSurchargeFee)
                .setScale(2, RoundingMode.HALF_UP);

        // 6. Cỗ máy tính toán Khuyến mãi (Voucher Engine - Read-Only)
        AppliedVoucherDto appliedVoucher = null;
        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (request.getVoucherCode() != null && !request.getVoucherCode().trim().isEmpty()) {
            String code = request.getVoucherCode().trim().toUpperCase();
            Voucher voucher = voucherRepository.findByCode(code)
                    .orElseThrow(() -> new AppException(ErrorCode.ERR_VOUCHER_NOT_FOUND, code));

            LocalDateTime now = LocalDateTime.now();

            // Kiểm tra hiệu lực thời gian và trạng thái
            if (!Boolean.TRUE.equals(voucher.getIsActive())
                    || now.isBefore(voucher.getStartDate())
                    || now.isAfter(voucher.getEndDate())) {
                throw new AppException(ErrorCode.ERR_VOUCHER_EXPIRED, code);
            }

            // Kiểm tra giá trị đơn hàng tối thiểu
            if (voucher.getMinOrderValue() != null && subtotal.compareTo(voucher.getMinOrderValue()) < 0) {
                throw new AppException(ErrorCode.ERR_VOUCHER_MIN_VALUE, voucher.getMinOrderValue().doubleValue());
            }

            // Tính mức giảm
            if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
                BigDecimal rawDiscount = subtotal.multiply(voucher.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (voucher.getMaxDiscountAmount() != null && rawDiscount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                    discountAmount = voucher.getMaxDiscountAmount();
                } else {
                    discountAmount = rawDiscount;
                }
            } else { // FIXED_AMOUNT
                discountAmount = voucher.getDiscountValue();
            }

            // Ràng buộc chặn vượt quá subtotal
            if (discountAmount.compareTo(subtotal) > 0) {
                discountAmount = subtotal;
            }
            discountAmount = discountAmount.setScale(2, RoundingMode.HALF_UP);

            appliedVoucher = AppliedVoucherDto.builder()
                    .voucherId(voucher.getId())
                    .code(voucher.getCode())
                    .title(voucher.getTitle())
                    .discountType(voucher.getDiscountType().name())
                    .discountValue(voucher.getDiscountValue().setScale(2, RoundingMode.HALF_UP))
                    .discountAmount(discountAmount)
                    .build();
        }

        // 7. Chốt FinalTotal = max(0, Subtotal - DiscountAmount)
        BigDecimal finalTotal = subtotal.subtract(discountAmount);
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalTotal = BigDecimal.ZERO;
        }
        finalTotal = finalTotal.setScale(2, RoundingMode.HALF_UP);

        // 8. Đóng gói kết quả phản hồi
        FinancialSummaryDto financialSummary = FinancialSummaryDto.builder()
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .finalTotal(finalTotal)
                .build();

        CategoryFeesDto categoryFees = CategoryFeesDto.builder()
                .baseTransportFee(baseTransportFee)
                .laborFee(laborFee)
                .technicalFee(technicalFee)
                .materialFee(materialFee)
                .terrainSurchargeFee(terrainSurchargeFee)
                .build();

        return PricingCalculationResponse.builder()
                .isEstimated(true)
                .calculatedDistanceKm(calculatedDistanceKm)
                .currency("VND")
                .disclaimer("Bảng giá mang tính chất tham khảo dự kiến. Báo giá chính thức sẽ được xác nhận sau khi hoàn tất khảo sát.")
                .financialSummary(financialSummary)
                .categoryFees(categoryFees)
                .breakdownItems(breakdownItems)
                .appliedVoucher(appliedVoucher)
                .build();
    }

    private void validateSchedule(PricingCalculationRequest request) {
        if (request.getSchedule() == null) {
            throw new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Thông tin lịch trình không được để trống");
        }
        LocalDateTime startAt = request.getSchedule().getDesiredStartAt();
        LocalDateTime endAt = request.getSchedule().getDesiredEndAt();

        if (startAt == null || endAt == null) {
            throw new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Thời gian bắt đầu và kết thúc không được để trống");
        }

        LocalDateTime now = LocalDateTime.now();
        int minLeadHours = pricingProperties.getMinLeadHours();

        if (startAt.isBefore(now.plusHours(minLeadHours))) {
            throw new AppException(ErrorCode.ERR_INVALID_SCHEDULE, minLeadHours);
        }

        if (endAt.isBefore(startAt) || endAt.isEqual(startAt)) {
            throw new AppException(ErrorCode.ERR_PRICE_INVALID_INPUT, "Thời gian kết thúc phải diễn ra sau thời gian bắt đầu");
        }
    }

    private FeeCategory determineCategory(Service service) {
        if (service == null) {
            return FeeCategory.TRANSPORT;
        }

        String typeName = service.getServiceType() != null ? normalizeText(service.getServiceType().getName()) : "";
        String serviceName = normalizeText(service.getName());

        if (typeName.contains("boc xep") || typeName.contains("khuan vac") || typeName.contains("nhan cong") || typeName.contains("labor")) {
            return FeeCategory.LABOR;
        }
        if (typeName.contains("thao lap") || typeName.contains("ky thuat") || typeName.contains("technical")) {
            return FeeCategory.TECHNICAL;
        }
        if (typeName.contains("vat tu") || typeName.contains("dong goi") || typeName.contains("material")) {
            return FeeCategory.MATERIAL;
        }
        if (typeName.contains("phu phi") || typeName.contains("dia hinh") || typeName.contains("thang bo") || typeName.contains("hem")
                || typeName.contains("terrain") || typeName.contains("surcharge")) {
            return FeeCategory.TERRAIN;
        }
        if (typeName.contains("xe") || typeName.contains("van chuyen") || typeName.contains("cuoc") || typeName.contains("transport")) {
            return FeeCategory.TRANSPORT;
        }

        // Kiểm tra bổ sung theo tên dịch vụ nếu tên loại dịch vụ chung chung
        if (serviceName.contains("boc xep") || serviceName.contains("khuan vac") || serviceName.contains("nhan cong")) {
            return FeeCategory.LABOR;
        }
        if (serviceName.contains("thao lap") || serviceName.contains("ky thuat") || serviceName.contains("may lanh") || serviceName.contains("giuong")) {
            return FeeCategory.TECHNICAL;
        }
        if (serviceName.contains("thung") || serviceName.contains("carton") || serviceName.contains("mang pe") || serviceName.contains("bang keo")) {
            return FeeCategory.MATERIAL;
        }
        if (serviceName.contains("thang bo") || serviceName.contains("hem") || serviceName.contains("phu phi")) {
            return FeeCategory.TERRAIN;
        }

        return FeeCategory.TRANSPORT;
    }

    private String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        String nfdNormalizedString = Normalizer.normalize(input, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(nfdNormalizedString).replaceAll("").toLowerCase();
    }
}

