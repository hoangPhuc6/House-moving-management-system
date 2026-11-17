package com.example.house_moving_service.modules.inventory.repository;

import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

// Chỉ INSERT (qua InventoryLedger) và đọc; không gọi delete / sửa dòng đã ghi
@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Integer>,
        JpaSpecificationExecutor<InventoryTransaction> {
}
