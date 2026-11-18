package com.example.house_moving_service.modules.inventory.repository;

import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

// Chỉ INSERT (qua InventoryLedger) và đọc; không gọi delete / sửa dòng đã ghi
@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Integer>,
        JpaSpecificationExecutor<InventoryTransaction> {

    /** Tổng quantity của một loại giao dịch. */
    interface TypeTotal {
        InventoryTransactionType getType();

        Long getTotal();
    }

    // Tổng quantity theo loại giao dịch của một vật tư, created_at trong [from, toExclusive)
    @Query("SELECT t.transactionType AS type, SUM(t.quantity) AS total FROM InventoryTransaction t "
            + "WHERE t.material.id = :materialId AND t.createdAt >= :from AND t.createdAt < :toExclusive "
            + "GROUP BY t.transactionType")
    List<TypeTotal> sumQuantityByType(@Param("materialId") Integer materialId,
                                      @Param("from") LocalDateTime from,
                                      @Param("toExclusive") LocalDateTime toExclusive);
}
