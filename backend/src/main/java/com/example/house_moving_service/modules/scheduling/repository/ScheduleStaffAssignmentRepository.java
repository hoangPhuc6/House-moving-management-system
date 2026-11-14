package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
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
import java.util.Optional;

@Repository
public interface ScheduleStaffAssignmentRepository extends JpaRepository<ScheduleStaffAssignment, Integer> {

    // Dòng phân công của một nhân viên trên chuyến (unique theo chuyến + nhân viên)
    Optional<ScheduleStaffAssignment> findByMovingScheduleIdAndStaffId(Integer movingScheduleId, Long staffId);

    // Phân công của các chuyến, kèm nhân viên và tài khoản (họ tên, số điện thoại)
    @Query("""
            SELECT a FROM ScheduleStaffAssignment a
            JOIN FETCH a.staff s
            LEFT JOIN FETCH s.user
            WHERE a.movingSchedule.id IN :scheduleIds
            ORDER BY a.id ASC
            """)
    List<ScheduleStaffAssignment> findAllWithStaffByScheduleIds(@Param("scheduleIds") Collection<Integer> scheduleIds);

    // Mọi dòng phân công nhân viên của chuyến, kể cả đã hủy (để dùng lại dòng cũ vì unique theo chuyến + nhân viên)
    List<ScheduleStaffAssignment> findAllByMovingScheduleId(Integer movingScheduleId);

    /**
     * Nhân viên trong staffIds đang được phân công (chưa hủy / chưa xong) cho chuyến chưa xong khác có khung giờ giao với
     * [start, end). Chạm biên không tính là trùng. excludeScheduleId = 0 nếu không cần loại trừ chuyến nào.
     */
    @Query("""
            SELECT DISTINCT a.staff.id FROM ScheduleStaffAssignment a
            WHERE a.staff.id IN :staffIds
              AND a.status NOT IN :closedStatuses
              AND a.movingSchedule.status IN :scheduleStatuses
              AND a.movingSchedule.id <> :excludeScheduleId
              AND a.movingSchedule.scheduledStartAt < :end
              AND a.movingSchedule.scheduledEndAt > :start
            """)
    List<Long> findConflictingStaffIds(@Param("staffIds") Collection<Long> staffIds,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       @Param("excludeScheduleId") Integer excludeScheduleId,
                                       @Param("closedStatuses") Collection<AssignmentStatus> closedStatuses,
                                       @Param("scheduleStatuses") Collection<MovingScheduleStatus> scheduleStatuses);

    default List<Long> findConflictingStaffIds(Collection<Long> staffIds, LocalDateTime start,
                                               LocalDateTime end, Integer excludeScheduleId) {
        return findConflictingStaffIds(staffIds, start, end, excludeScheduleId,
                EnumSet.of(AssignmentStatus.CANCELLED, AssignmentStatus.COMPLETED),
                MovingScheduleStatus.UNFINISHED);
    }
}
