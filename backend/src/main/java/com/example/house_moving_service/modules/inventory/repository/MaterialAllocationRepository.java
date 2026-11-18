package com.example.house_moving_service.modules.inventory.repository;

import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import com.example.house_moving_service.modules.inventory.enums.AllocationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialAllocationRepository extends JpaRepository<MaterialAllocation, Integer> {

    // Khóa dòng cấp phát (SELECT ... FOR UPDATE) trước khi hoàn / hủy / chốt, để không xử lý một dòng hai lần
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM MaterialAllocation a WHERE a.id = :id")
    Optional<MaterialAllocation> findByIdForUpdate(@Param("id") Integer id);

    // Khóa mọi dòng của chuyến ở một trạng thái, theo materialId tăng dần (đúng thứ tự khóa vật tư sau đó).
    // Gọi sau khi đã khóa chuyến
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM MaterialAllocation a WHERE a.schedule.id = :scheduleId AND a.status = :status "
            + "ORDER BY a.material.id ASC")
    List<MaterialAllocation> findAllByScheduleIdAndStatusForUpdate(@Param("scheduleId") Integer scheduleId,
                                                                   @Param("status") AllocationStatus status);

    // Mọi dòng của chuyến (mọi trạng thái) để trả response
    @EntityGraph(attributePaths = "material")
    List<MaterialAllocation> findAllByScheduleIdOrderByIdAsc(Integer scheduleId);

    // Mọi dòng của đơn (mọi chuyến, mọi trạng thái), theo chuyến rồi id tăng dần
    @EntityGraph(attributePaths = "material")
    List<MaterialAllocation> findAllByBookingIdOrderByScheduleIdAscIdAsc(Integer bookingId);

    // Chỉ lấy id chuyến (không nạp entity) để khóa chuyến trước, rồi mới khóa dòng cấp phát
    @Query("SELECT a.schedule.id FROM MaterialAllocation a WHERE a.id = :id")
    Optional<Integer> findScheduleIdById(@Param("id") Integer id);

    // Các chuyến của đơn còn dòng ở trạng thái này, id tăng dần (listener hủy đơn)
    @Query("SELECT DISTINCT a.schedule.id FROM MaterialAllocation a WHERE a.booking.id = :bookingId "
            + "AND a.status = :status ORDER BY a.schedule.id")
    List<Integer> findScheduleIdsByBookingIdAndStatus(@Param("bookingId") Integer bookingId,
                                                      @Param("status") AllocationStatus status);
}
