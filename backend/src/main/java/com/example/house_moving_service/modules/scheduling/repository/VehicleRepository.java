package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Integer>, JpaSpecificationExecutor<Vehicle> {

    @EntityGraph(attributePaths = "vehicleType")
    Optional<Vehicle> findWithTypeById(Integer id);

    boolean existsByLicensePlateIgnoreCase(String licensePlate);

    boolean existsByLicensePlateIgnoreCaseAndIdNot(String licensePlate, Integer id);

    long countByVehicleTypeId(Integer vehicleTypeId);

    // Số xe theo từng loại, dùng cho danh sách loại xe
    @Query("SELECT v.vehicleType.id AS vehicleTypeId, COUNT(v) AS total FROM Vehicle v GROUP BY v.vehicleType.id")
    List<VehicleCountByType> countGroupByType();

    interface VehicleCountByType {
        Integer getVehicleTypeId();

        Long getTotal();
    }

    /**
     * Xe có trạng thái thuộc statuses và không nằm trong chuyến chưa xong nào (trừ excludeScheduleId) có khung giờ
     * giao với [start, end); kèm loại xe. Cùng điều kiện trùng với ScheduleVehicleAssignmentRepository#findConflictingVehicleIds.
     */
    @Query("""
            SELECT v FROM Vehicle v JOIN FETCH v.vehicleType t
            WHERE v.status IN :statuses
              AND NOT EXISTS (
                  SELECT 1 FROM ScheduleVehicleAssignment a
                  WHERE a.vehicle = v
                    AND a.status NOT IN :closedStatuses
                    AND a.movingSchedule.status IN :scheduleStatuses
                    AND a.movingSchedule.id <> :excludeScheduleId
                    AND a.movingSchedule.scheduledStartAt < :end
                    AND a.movingSchedule.scheduledEndAt > :start)
            ORDER BY t.name ASC, v.licensePlate ASC
            """)
    List<Vehicle> findAvailable(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end,
                                @Param("excludeScheduleId") Integer excludeScheduleId,
                                @Param("statuses") Collection<VehicleStatus> statuses,
                                @Param("closedStatuses") Collection<AssignmentStatus> closedStatuses,
                                @Param("scheduleStatuses") Collection<MovingScheduleStatus> scheduleStatuses);
}
