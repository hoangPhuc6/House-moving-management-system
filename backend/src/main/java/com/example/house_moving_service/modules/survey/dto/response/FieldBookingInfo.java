package com.example.house_moving_service.modules.survey.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Thông tin đơn hàng MOVER cần khi đi khảo sát: liên hệ ai, khách muốn chuyển khi nào, khách dặn gì
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldBookingInfo {

    private Integer bookingId;
    private String bookingCode;
    private String contactName;
    private String contactPhone;
    private LocalDateTime desiredStartAt;
    private String note;
}
