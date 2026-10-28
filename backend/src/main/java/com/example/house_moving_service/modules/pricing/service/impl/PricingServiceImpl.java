package com.example.house_moving_service.modules.pricing.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.pricing.config.PricingProperties;
import com.example.house_moving_service.modules.pricing.dto.PricingInput;
import com.example.house_moving_service.modules.pricing.dto.PricingInput.AccessInfo;
import com.example.house_moving_service.modules.pricing.dto.PricingInput.ServiceLine;
import com.example.house_moving_service.modules.pricing.dto.request.LocationDto;
import com.example.house_moving_service.modules.pricing.dto.request.PricingCalculationRequest;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.modules.pricing.engine.PricingEngine;
import com.example.house_moving_service.modules.pricing.helper.RouteDistanceHelper;
import com.example.house_moving_service.modules.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PricingServiceImpl implements PricingService {

    private final PricingProperties pricingProperties;
    private final RouteDistanceHelper routeDistanceHelper;
    private final PricingEngine pricingEngine;

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
        BigDecimal distanceKm = routeDistanceHelper.resolveDistance(request.getRoute());

        // 3. Tính giá bằng lõi PricingEngine
        return pricingEngine.calculate(toPricingInput(request, distanceKm));
    }

    private PricingInput toPricingInput(PricingCalculationRequest request, BigDecimal distanceKm) {
        List<ServiceLine> lines = request.getServiceItems() == null ? List.of() : request.getServiceItems().stream()
                .map(item -> new ServiceLine(item.getServiceId(), item.getQuantity() != null ? item.getQuantity() : 0))
                .toList();
        return new PricingInput(
                distanceKm,
                toAccessInfo(request.getRoute().getPickup()),
                toAccessInfo(request.getRoute().getDropoff()),
                lines,
                request.getVoucherCode());
    }

    private AccessInfo toAccessInfo(LocationDto location) {
        if (location == null) {
            return new AccessInfo(0, false, 0);
        }
        return new AccessInfo(
                location.getFloorNumber() != null ? location.getFloorNumber() : 0,
                Boolean.TRUE.equals(location.getHasElevator()),
                location.getWalkingDistanceMeters() != null ? location.getWalkingDistanceMeters() : 0);
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
}
