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
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingDetailResponse {

    private Integer id;
    private String bookingCode;
    private Long customerId;
    private String customerName;
    private String contactName;
    private String contactPhone;
    private BookingStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal discountAmount;

    private String note;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime desiredStartAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime desiredEndAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    private BookingRouteResponse route;

    @Builder.Default
    private List<BookingItemResponse> items = new ArrayList<>();

    @Builder.Default
    private List<BookingServiceResponse> services = new ArrayList<>();

    @Builder.Default
    private List<BookingVoucherResponse> vouchers = new ArrayList<>();

    @Builder.Default
    private List<BookingStatusHistoryResponse> statusHistories = new ArrayList<>();
}

