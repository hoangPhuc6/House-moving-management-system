package com.example.house_moving_service.modules.catalog.dto.response;

import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceResponse {

    private Integer id;
    private Integer serviceTypeId;
    private String serviceTypeName;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private ServiceStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

