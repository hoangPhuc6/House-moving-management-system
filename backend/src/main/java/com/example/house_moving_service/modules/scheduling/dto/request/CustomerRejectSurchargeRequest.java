package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khách từ chối báo giá điều chỉnh: bắt buộc lý do. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRejectSurchargeRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    @Size(max = 2000, message = "Lý do từ chối tối đa 2000 ký tự")
    private String customerNote;
}
