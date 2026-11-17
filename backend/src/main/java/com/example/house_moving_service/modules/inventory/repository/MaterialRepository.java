package com.example.house_moving_service.modules.inventory.repository;

import com.example.house_moving_service.modules.inventory.entity.Material;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Integer>, JpaSpecificationExecutor<Material> {

    // Khóa dòng (SELECT ... FOR UPDATE) để hai thao tác đổi tồn kho không ghi đè nhau. Chỉ InventoryLedger
    // và service cần đọc vật tư đã khóa (theo materialId tăng dần) mới gọi
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Material m WHERE m.id = :id")
    Optional<Material> findByIdForUpdate(@Param("id") Integer id);

    boolean existsByCodeIgnoreCase(String code);
}
