package com.example.house_moving_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class UpdateServiceTypeRequest {

    @NotBlank(message = "Tên loại dịch vụ không được để trống")
    @Size(max = 255, message = "Tên loại dịch vụ không được vượt quá 255 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Trạng thái kích hoạt không được để trống")
    private Boolean isActive;
}

