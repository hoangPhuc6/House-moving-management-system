package com.example.house_moving_service.modules.inventory.repository;

import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaterialAllocationRepository extends JpaRepository<MaterialAllocation, Integer> {

    // Khóa dòng cấp phát (SELECT ... FOR UPDATE) trước khi hoàn / hủy / chốt, để không xử lý một dòng hai lần
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM MaterialAllocation a WHERE a.id = :id")
    Optional<MaterialAllocation> findByIdForUpdate(@Param("id") Integer id);
}
