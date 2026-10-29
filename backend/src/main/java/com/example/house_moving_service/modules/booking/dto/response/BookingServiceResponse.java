package com.example.house_moving_service.modules.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class BookingServiceResponse {

    private Integer id;
    private Integer serviceId;
    private String serviceName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal priceApplied;

    private Integer quantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalPrice;

    private String note;

    // true: phụ phí hệ thống tự cộng khi tính giá (dịch vụ có mã), không phải dịch vụ khách chọn
    private boolean autoApplied;
}

