package com.example.house_moving_service.modules.identity.service;

import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.enums.RoleCode;

import java.util.List;
import java.util.Optional;

/**
 * Tra cứu nhân viên dành cho module khác. Không ném ErrorCode của module gọi:
 * trả boolean / Optional để module gọi tự quyết định lỗi.
 */
public interface StaffLookupService {

    // Kiểm tra xem nhân viên có trạng thái hoạt động và có role cần kiểm tra.
    boolean isActiveStaffWithRole(Long userId, RoleCode role);

    // Reference (proxy) tới nhân viên để gắn quan hệ JPA, không truy vấn database ngay. 
    Staff getReference(Long userId);

    Optional<StaffSummary> findSummary(Long userId);

    // Nhân viên hợp lệ theo role, sắp theo họ tên — dùng cho danh sách phân công.
    List<StaffSummary> findActiveByRole(RoleCode role);
}
