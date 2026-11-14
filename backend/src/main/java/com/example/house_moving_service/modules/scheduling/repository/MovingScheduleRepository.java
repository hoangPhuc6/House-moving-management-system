package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovingScheduleRepository extends JpaRepository<MovingSchedule, Integer>, JpaSpecificationExecutor<MovingSchedule> {

    // Khóa dòng (SELECT ... FOR UPDATE) đến hết transaction để hai thao tác đổi trạng thái không ghi đè nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MovingSchedule s WHERE s.id = :id")
    Optional<MovingSchedule> findByIdForUpdate(@Param("id") Integer id);

    // Mọi chuyến của đơn, sắp theo giờ hẹn bắt đầu
    List<MovingSchedule> findAllByBookingIdOrderByScheduledStartAtAscIdAsc(Integer bookingId);

    // Còn chuyến nào khác của đơn ở các trạng thái truyền vào không (dùng khi hoàn tất chuyến)
    boolean existsByBookingIdAndIdNotAndStatusIn(Integer bookingId, Integer excludeId,
                                                 Collection<MovingScheduleStatus> statuses);

    /**
     * Khóa tư vấn (advisory lock) của PostgreSQL, tự nhả khi transaction kết thúc. Dùng để hai điều phối viên không
     * cùng lúc phân công một xe / một nhân viên vào hai chuyến trùng giờ (kiểm tra trùng rồi mới ghi nên cần tuần tự).
     * namespace phân biệt loại tài nguyên (xe / nhân viên), key là ID.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:namespace, :key)", nativeQuery = true)
    Integer acquireTransactionLock(@Param("namespace") int namespace, @Param("key") int key);
}
