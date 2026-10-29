package com.example.house_moving_service.modules.quotation.dto.response;

import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotationResponse {

    private Integer id;
    private Integer bookingId;
    private String bookingCode;
    private Integer version;
    private BigDecimal amount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private QuotationStatus status;
    private String note;
    private LocalDateTime validUntil;
    private Long createdById;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;
    private List<QuotationItemResponse> items;
}
