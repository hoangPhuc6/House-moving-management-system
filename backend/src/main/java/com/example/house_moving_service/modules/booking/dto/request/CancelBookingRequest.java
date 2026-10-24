package com.example.house_moving_service.modules.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelBookingRequest {

    @NotBlank(message = "Vui lòng nhập lý do hủy đơn")
    @Size(max = 500, message = "Lý do hủy đơn không được vượt quá 500 ký tự")
    private String reason;
}

