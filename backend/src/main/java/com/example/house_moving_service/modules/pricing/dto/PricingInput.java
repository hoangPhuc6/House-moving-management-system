package com.example.house_moving_service.modules.pricing.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Đầu vào thuần của PricingEngine: đã có sẵn khoảng cách, không còn lịch / tọa độ.
 * Giá tham khảo (PricingService) và báo giá chính thức (Quotation) cùng dựng đầu vào này.
 */
public record PricingInput(
        BigDecimal distanceKm,
        AccessInfo pickup,
        AccessInfo dropoff,
        List<ServiceLine> services,
        String voucherCode
) {

    // Điều kiện tiếp cận một điểm (dùng cho phụ phí tự động ở task 2.3)
    public record AccessInfo(int floorNumber, boolean hasElevator, int walkingDistanceMeters) {
    }

    public record ServiceLine(Integer serviceId, int quantity) {
    }
}
