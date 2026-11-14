package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bác đề xuất hoặc rút khoản đã gửi khách: bắt buộc lý do. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurchargeReviewNoteRequest {

    @NotBlank(message = "Lý do không được để trống")
    @Size(max = 2000, message = "Lý do tối đa 2000 ký tự")
    private String reviewNote;
}
