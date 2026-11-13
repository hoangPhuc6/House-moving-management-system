package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body của bắt đầu / hoàn tất chuyến; không bắt buộc. note được nối vào nhật ký chuyến. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovingNoteRequest {

    @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
    private String note;
}
