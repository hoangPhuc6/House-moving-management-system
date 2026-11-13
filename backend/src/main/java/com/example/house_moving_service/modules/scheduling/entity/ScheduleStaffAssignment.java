package com.example.house_moving_service.modules.scheduling.entity;

import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Nhân viên được phân công vào một chuyến. Mỗi chuyến tối đa 1 SUPERVISOR chưa hủy (uq_ssa_one_supervisor). */
@Entity
@Table(name = "schedule_staff_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleStaffAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moving_schedule_id", nullable = false)
    private MovingSchedule movingSchedule;

    // Join theo @Id của Staff (= user_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private StaffAssignmentRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssignmentStatus status;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = AssignmentStatus.PLANNED;
        }
    }
}
