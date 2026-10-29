package com.example.house_moving_service.modules.survey.dto.response;

import com.example.house_moving_service.modules.survey.enums.SlotBusyReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** Lịch rảnh / bận của một nhân viên trong một ngày: mỗi khung giờ đang bật một phần tử, sắp theo giờ bắt đầu. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAvailabilityResponse {

    private Long staffId;
    private String fullName;
    private LocalDate date;
    private List<SlotAvailability> slots;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SlotAvailability {

        private SurveyTimeSlotResponse slot;
        private boolean available;

        // null khi rảnh
        private SlotBusyReason reason;

        // Lịch khảo sát đang chiếm khung giờ; chỉ có khi reason = SURVEY
        private Integer surveyId;
    }
}
