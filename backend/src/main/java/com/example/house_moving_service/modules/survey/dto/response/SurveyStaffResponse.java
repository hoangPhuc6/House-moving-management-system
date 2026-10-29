package com.example.house_moving_service.modules.survey.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Nhân viên khảo sát gắn với một lịch. Chỉ lấy field nằm trên bảng staff để không phải nạp thêm users khi phân trang
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyStaffResponse {

    private Long userId;
    private String staffCode;
    private String fullName;
}
