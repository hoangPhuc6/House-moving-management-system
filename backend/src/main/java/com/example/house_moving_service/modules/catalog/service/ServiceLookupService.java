package com.example.house_moving_service.modules.catalog.service;

import com.example.house_moving_service.modules.catalog.entity.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Tra cứu entity Service dành cho module khác (pricing, booking) — tách khỏi ServiceCatalogService
 * (API cho controller) để module khác chỉ phụ thuộc đúng phần cần dùng.
 */
public interface ServiceLookupService {

    /** Trả về các dịch vụ có id trong danh sách; id không tồn tại thì bị bỏ qua (người gọi tự kiểm tra). */
    List<Service> findAllByIds(Collection<Integer> ids);

    /** Reference (proxy) tới dịch vụ để gắn quan hệ JPA, không truy vấn database ngay. */
    Service getReference(Integer id);

    // Dịch vụ hệ thống tự cộng theo mã cố định (SystemServiceCode), kèm loại dịch vụ. Không có → Optional.empty()
    Optional<Service> findByCode(String code);
}
