package com.example.house_moving_service.modules.survey.enums;

// Lý do một khung giờ của nhân viên đang bận, trả cho FE khi xem lịch rảnh / bận
public enum SlotBusyReason {
    SURVEY, // Đã có lịch khảo sát CONFIRMED / IN_PROGRESS ở khung giờ đó
    
    OTHER_SCHEDULE // Module khác (Lập lịch) báo nhân viên có việc trong khoảng giờ đó, ví dụ đi chuyển nhà
}
