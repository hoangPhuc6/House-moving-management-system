package com.example.house_moving_service.modules.booking.dto.response;

import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingSummaryResponse {

    private Integer id;
    private String bookingCode;
    private BookingStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal discountAmount;

    private String pickupAddress;
    private String dropoffAddress;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime desiredStartAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}

