package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.Incident;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Integer>, JpaSpecificationExecutor<Incident> {

    // Khóa dòng (SELECT ... FOR UPDATE) để hai thao tác đổi trạng thái sự cố không ghi đè nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Incident i WHERE i.id = :id")
    Optional<Incident> findByIdForUpdate(@Param("id") Integer id);

    // Sự cố của một chuyến, mới nhất trước
    List<Incident> findAllByScheduleIdOrderByReportedAtDescIdDesc(Integer scheduleId);

    // Sự cố của một đơn, mới nhất trước
    List<Incident> findAllByBookingIdOrderByReportedAtDescIdDesc(Integer bookingId);
}
