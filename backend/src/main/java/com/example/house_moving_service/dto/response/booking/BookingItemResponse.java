package com.example.house_moving_service.dto.response.booking;

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
public class BookingItemResponse {

    private Integer id;
    private String itemName;
    private Integer quantity;
    private BigDecimal estimatedVolumeM3;
    private Boolean isFragile;
    private Boolean packingRequired;
    private Boolean disassemblyRequired;
    private String note;
}

