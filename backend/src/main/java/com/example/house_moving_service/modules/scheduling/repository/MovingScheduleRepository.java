package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovingScheduleRepository extends JpaRepository<MovingSchedule, Integer> {

    // Khóa dòng (SELECT ... FOR UPDATE) đến hết transaction để hai thao tác đổi trạng thái không ghi đè nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MovingSchedule s WHERE s.id = :id")
    Optional<MovingSchedule> findByIdForUpdate(@Param("id") Integer id);

    // Mọi chuyến của đơn, sắp theo giờ hẹn bắt đầu
    List<MovingSchedule> findAllByBookingIdOrderByScheduledStartAtAscIdAsc(Integer bookingId);

    // Còn chuyến nào khác của đơn ở các trạng thái truyền vào không (dùng khi hoàn tất chuyến)
    boolean existsByBookingIdAndIdNotAndStatusIn(Integer bookingId, Integer excludeId,
                                                 Collection<MovingScheduleStatus> statuses);
}
