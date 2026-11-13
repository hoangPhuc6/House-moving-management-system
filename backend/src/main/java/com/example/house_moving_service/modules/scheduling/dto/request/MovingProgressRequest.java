package com.example.house_moving_service.modules.scheduling.dto.request;

import com.example.house_moving_service.modules.scheduling.enums.ProgressMilestone;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Báo một mốc tiến độ. Mốc OTHER bắt buộc có note. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovingProgressRequest {

    @NotNull(message = "Mốc tiến độ không được để trống")
    private ProgressMilestone milestone;

    @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
    private String note;
}
