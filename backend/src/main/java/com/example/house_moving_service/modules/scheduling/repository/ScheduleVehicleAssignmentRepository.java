package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.ScheduleVehicleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ScheduleVehicleAssignmentRepository extends JpaRepository<ScheduleVehicleAssignment, Integer> {

    // Xe của các chuyến, kèm loại xe
    @Query("""
            SELECT a FROM ScheduleVehicleAssignment a
            JOIN FETCH a.vehicle v
            JOIN FETCH v.vehicleType
            WHERE a.movingSchedule.id IN :scheduleIds
            ORDER BY a.id ASC
            """)
    List<ScheduleVehicleAssignment> findAllWithVehicleByScheduleIds(@Param("scheduleIds") Collection<Integer> scheduleIds);
}
