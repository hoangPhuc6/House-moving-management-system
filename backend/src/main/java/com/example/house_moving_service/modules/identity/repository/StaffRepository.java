package com.example.house_moving_service.modules.identity.repository;

import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    // Nhân viên hợp lệ = hồ sơ staff ACTIVE + tài khoản đang hoạt động + có role cần kiểm tra
    @Query("""
            SELECT COUNT(s) > 0 FROM Staff s JOIN s.user u JOIN u.roles r
            WHERE s.id = :userId AND s.employmentStatus = 'ACTIVE'
              AND u.isActive = true AND r.code = :roleCode
            """)
    boolean existsActiveByIdAndRole(@Param("userId") Long userId, @Param("roleCode") RoleCode roleCode);

    @Query("""
            SELECT s FROM Staff s JOIN FETCH s.user u JOIN u.roles r
            WHERE s.employmentStatus = 'ACTIVE' AND u.isActive = true AND r.code = :roleCode
            ORDER BY s.fullName
            """)
    List<Staff> findAllActiveByRole(@Param("roleCode") RoleCode roleCode);
}
