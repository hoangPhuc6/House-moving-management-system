package com.example.house_moving_service.modules.scheduling.entity;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
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
import java.time.format.DateTimeFormatter;

/**
 * Một chuyến vận chuyển của đơn hàng. Một đơn có thể có nhiều chuyến (chia ngày, dời lịch...).
 * note là nhật ký chỉ nối thêm: mỗi dòng "[yyyy-MM-dd HH:mm][TAG][user#id] nội dung" (xem MovingLogParser).
 */
@Entity
@Table(name = "moving_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovingSchedule {

    public static final DateTimeFormatter NOTE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "scheduled_start_at", nullable = false)
    private LocalDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private LocalDateTime scheduledEndAt;

    @Column(name = "actual_start_at")
    private LocalDateTime actualStartAt;

    @Column(name = "actual_end_at")
    private LocalDateTime actualEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MovingScheduleStatus status;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = MovingScheduleStatus.PLANNED;
        }
    }

    /** Nối một dòng vào nhật ký, không ghi đè nội dung cũ. */
    public void appendNote(String tag, Long actorUserId, String content, LocalDateTime at) {
        String actor = actorUserId != null ? "user#" + actorUserId : "system";
        String line = "[" + at.format(NOTE_TIME_FORMAT) + "][" + tag + "][" + actor + "] "
                + (content != null ? content.trim() : "");
        this.note = this.note == null || this.note.isBlank() ? line : this.note + "\n" + line;
    }
}
