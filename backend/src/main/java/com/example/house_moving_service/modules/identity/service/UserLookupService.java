package com.example.house_moving_service.modules.identity.service;

import com.example.house_moving_service.modules.identity.entity.User;

/**
 * Tra cứu tài khoản (users) dành cho module khác (booking, quotation, survey...) — tách khỏi UserService
 * (API cho controller) để module khác chỉ phụ thuộc đúng phần cần dùng.
 */
public interface UserLookupService {

    /** Reference (proxy) tới user để gắn quan hệ JPA (changed_by, created_by...), không truy vấn database ngay. */
    User getUserReference(Long userId);
}
