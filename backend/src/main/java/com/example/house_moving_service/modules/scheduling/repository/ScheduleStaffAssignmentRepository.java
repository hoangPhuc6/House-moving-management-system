package com.example.house_moving_service.modules.scheduling.repository;

import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    // Nhân viên có phân công role này (chưa hủy) trên một chuyến chưa hủy nào đó của đơn không
    @Query("""
            SELECT COUNT(a) > 0 FROM ScheduleStaffAssignment a
            WHERE a.movingSchedule.booking.id = :bookingId
              AND a.staff.id = :staffId
              AND a.role = :role
              AND a.status <> :cancelledAssignment
              AND a.movingSchedule.status <> :cancelledSchedule
            """)
    boolean existsActiveRoleOnBooking(@Param("bookingId") Integer bookingId,
                                      @Param("staffId") Long staffId,
                                      @Param("role") StaffAssignmentRole role,
                                      @Param("cancelledAssignment") AssignmentStatus cancelledAssignment,
                                      @Param("cancelledSchedule") MovingScheduleStatus cancelledSchedule);
}
