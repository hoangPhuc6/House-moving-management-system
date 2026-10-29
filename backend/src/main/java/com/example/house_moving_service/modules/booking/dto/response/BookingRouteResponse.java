package com.example.house_moving_service.modules.booking.dto.response;

import com.example.house_moving_service.common.enums.PropertyType;
import com.example.house_moving_service.modules.booking.dto.RouteAccessInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingRouteResponse {

    private Integer id;
    private String pickupAddress;
    private BigDecimal pickupLat;
    private BigDecimal pickupLng;
    private PropertyType pickupPropertyType;
    private String pickupNote;
    private String dropoffAddress;
    private BigDecimal dropoffLat;
    private BigDecimal dropoffLng;
    private PropertyType dropoffPropertyType;
    private String dropoffNote;
    private BigDecimal estimatedDistanceKm;
    // Tầng / thang máy / cự ly hẻm tách từ pickupNote, dropoffNote
    private RouteAccessInfo pickupAccess;
    private RouteAccessInfo dropoffAccess;
}

