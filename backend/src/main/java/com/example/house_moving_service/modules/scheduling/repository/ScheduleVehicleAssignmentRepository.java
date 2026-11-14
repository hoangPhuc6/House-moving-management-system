package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.ScheduleVehicleAssignment;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
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

    // Phân công xe còn hiệu lực (chưa hủy / chưa xong) thuộc chuyến có trạng thái nằm trong scheduleStatuses
    @Query("""
            SELECT COUNT(a) > 0 FROM ScheduleVehicleAssignment a
            WHERE a.vehicle.id = :vehicleId
              AND a.status NOT IN :closedStatuses
              AND a.movingSchedule.status IN :scheduleStatuses
            """)
    boolean existsOpenAssignment(@Param("vehicleId") Integer vehicleId,
                                 @Param("closedStatuses") Collection<AssignmentStatus> closedStatuses,
                                 @Param("scheduleStatuses") Collection<MovingScheduleStatus> scheduleStatuses);

    /** Xe còn được phân công cho chuyến chưa hoàn thành (PLANNED, CONFIRMED, IN_PROGRESS, RESCHEDULED). */
    default boolean hasUnfinishedAssignment(Integer vehicleId) {
        return existsOpenAssignment(vehicleId,
                EnumSet.of(AssignmentStatus.CANCELLED, AssignmentStatus.COMPLETED),
                MovingScheduleStatus.UNFINISHED);
    }

    /** Xe đang chạy một chuyến IN_PROGRESS. */
    default boolean isOnTripInProgress(Integer vehicleId) {
        return existsOpenAssignment(vehicleId,
                EnumSet.of(AssignmentStatus.CANCELLED, AssignmentStatus.COMPLETED),
                EnumSet.of(MovingScheduleStatus.IN_PROGRESS));
    }

    // Mọi dòng phân công xe của chuyến, kể cả đã hủy (để dùng lại dòng cũ vì unique theo chuyến + xe)
    List<ScheduleVehicleAssignment> findAllByMovingScheduleId(Integer movingScheduleId);

    /**
     * Xe trong vehicleIds đang được phân công (chưa hủy / chưa xong) cho chuyến chưa xong khác có khung giờ giao với
     * [start, end). Chạm biên (chuyến này kết thúc đúng lúc chuyến kia bắt đầu) không tính là trùng.
     */
    @Query("""
            SELECT DISTINCT a.vehicle.id FROM ScheduleVehicleAssignment a
            WHERE a.vehicle.id IN :vehicleIds
              AND a.status NOT IN :closedStatuses
              AND a.movingSchedule.status IN :scheduleStatuses
              AND a.movingSchedule.id <> :excludeScheduleId
              AND a.movingSchedule.scheduledStartAt < :end
              AND a.movingSchedule.scheduledEndAt > :start
            """)
    List<Integer> findConflictingVehicleIds(@Param("vehicleIds") Collection<Integer> vehicleIds,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("excludeScheduleId") Integer excludeScheduleId,
                                            @Param("closedStatuses") Collection<AssignmentStatus> closedStatuses,
                                            @Param("scheduleStatuses") Collection<MovingScheduleStatus> scheduleStatuses);

    default List<Integer> findConflictingVehicleIds(Collection<Integer> vehicleIds, LocalDateTime start,
                                                    LocalDateTime end, Integer excludeScheduleId) {
        return findConflictingVehicleIds(vehicleIds, start, end, excludeScheduleId,
                EnumSet.of(AssignmentStatus.CANCELLED, AssignmentStatus.COMPLETED),
                MovingScheduleStatus.UNFINISHED);
    }
}
