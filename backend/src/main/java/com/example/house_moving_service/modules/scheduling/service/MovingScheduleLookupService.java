package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSnapshot;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;

/**
 * Tra cứu chuyến vận chuyển dành cho module khác (inventory...) — tách khỏi các service API của scheduling để module
 * khác chỉ phụ thuộc đúng phần cần dùng. Mọi method ném ERR_MOVING_SCHEDULE_NOT_FOUND nếu chuyến không tồn tại
 * (trừ isAssigned).
 */
public interface MovingScheduleLookupService {

    /** Reference (proxy) tới chuyến để gắn quan hệ JPA. */
    MovingSchedule getReference(Integer scheduleId);

    MovingScheduleSnapshot getSnapshot(Integer scheduleId);

    /**
     * Khóa dòng chuyến (SELECT ... FOR UPDATE) đến hết transaction của người gọi rồi trả snapshot, để người gọi kiểm
     * tra trạng thái trước khi ghi (ví dụ xuất / hoàn kho). Phải gọi trong một transaction đang mở.
     */
    MovingScheduleSnapshot lockForUpdate(Integer scheduleId);

    /** Nhân viên (users.id) có phân công chưa CANCELLED trên chuyến không; chuyến không tồn tại thì false. */
    boolean isAssigned(Integer scheduleId, Long userId);
}
