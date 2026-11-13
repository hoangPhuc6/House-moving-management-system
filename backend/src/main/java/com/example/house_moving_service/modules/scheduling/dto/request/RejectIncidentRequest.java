package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bác bỏ sự cố: bắt buộc lý do. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectIncidentRequest {

    @NotBlank(message = "Lý do bác bỏ không được để trống")
    @Size(max = 5000, message = "Lý do bác bỏ tối đa 5000 ký tự")
    private String resolutionNote;
}
