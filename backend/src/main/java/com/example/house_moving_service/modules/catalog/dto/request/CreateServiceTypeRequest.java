package com.example.house_moving_service.modules.catalog.dto.request;

import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
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
public class CreateServiceTypeRequest {

    @NotBlank(message = "Tên loại dịch vụ không được để trống")
    @Size(max = 255, message = "Tên loại dịch vụ không được vượt quá 255 ký tự")
    private String name;

    private String description;

    @Builder.Default
    private Boolean isActive = true;

    // Không bắt buộc: để trống thì dùng TRANSPORT
    private FeeCategory feeCategory;
}

