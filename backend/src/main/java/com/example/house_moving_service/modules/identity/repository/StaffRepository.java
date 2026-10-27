package com.example.house_moving_service.modules.identity.repository;

import com.example.house_moving_service.modules.identity.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, Long> {
}
