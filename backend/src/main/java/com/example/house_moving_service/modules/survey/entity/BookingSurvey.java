package com.example.house_moving_service.modules.survey.entity;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Một lần khảo sát của booking; khảo sát lại là một bản ghi mới. */
@Entity
@Table(name = "booking_surveys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSurvey {

    private static final DateTimeFormatter NOTE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "survey_method", nullable = false, length = 20)
    private SurveyMethod surveyMethod;

    @Column(name = "survey_date")
    private LocalDate surveyDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_time_slot_id")
    private SurveyTimeSlot timeSlot;

    // Join theo @Id của Staff (= user_id), giống bookings.customer_id -> customers(user_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_staff_id")
    private Staff surveyStaff;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SurveyStatus status;

    @Column(name = "actual_start_at")
    private LocalDateTime actualStartAt;

    @Column(name = "actual_end_at")
    private LocalDateTime actualEndAt;

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
            this.status = SurveyStatus.PENDING;
        }
    }

    /** Nối một dòng vào note, không ghi đè nội dung cũ. Dùng cho lý do hủy / đổi lịch / thất bại...
     */
    public void appendNote(String tag, Long actorUserId, String content) {
        appendNote(tag, actorUserId, content, LocalDateTime.now());
    }

    public void appendNote(String tag, Long actorUserId, String content, LocalDateTime at) {
        String actor = actorUserId != null ? "user#" + actorUserId : "system";
        String line = "[" + at.format(NOTE_TIME_FORMAT) + "][" + tag + "][" + actor + "] "
                + (content != null ? content.trim() : "");
        this.note = this.note == null || this.note.isBlank() ? line : this.note + "\n" + line;
    }
}
