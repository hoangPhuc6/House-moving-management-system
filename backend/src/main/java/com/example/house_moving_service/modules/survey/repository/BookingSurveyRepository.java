package com.example.house_moving_service.modules.survey.repository;

import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingSurveyRepository extends JpaRepository<BookingSurvey, Integer>, JpaSpecificationExecutor<BookingSurvey> {

    Optional<BookingSurvey> findFirstByBookingIdAndStatusIn(Integer bookingId, Collection<SurveyStatus> statuses);

    // Lần khảo sát mới nhất của booking
    Optional<BookingSurvey> findTopByBookingIdOrderByCreatedAtDesc(Integer bookingId);

    // Toàn bộ các lần khảo sát của booking, mới nhất trước
    List<BookingSurvey> findAllByBookingIdOrderByCreatedAtDesc(Integer bookingId);

    // Khung giờ có đang được dùng cho lịch khảo sát từ ngày fromDate trở đi không (chặn đổi giờ của khung giờ đó)
    boolean existsByTimeSlotIdAndStatusInAndSurveyDateGreaterThanEqual(Integer timeSlotId,
                                                                      Collection<SurveyStatus> statuses,
                                                                      LocalDate fromDate);

    // Trong ngày đó, khung giờ đó, nhân viên đó đã nhận lịch khảo sát nào ở các trạng thái truyền vào chưa.
    // excludeId: id của chính lịch đang đổi, null khi tạo mới 
    @Query("""
            SELECT COUNT(s) > 0 FROM BookingSurvey s
            WHERE s.surveyStaff.id = :staffId
              AND s.surveyDate = :date
              AND s.timeSlot.id = :slotId
              AND s.status IN :statuses
              AND (:excludeId IS NULL OR s.id <> :excludeId)
            """)
    boolean existsSurveySlotConflict(@Param("staffId") Long staffId,
                                     @Param("date") LocalDate date,
                                     @Param("slotId") Integer slotId,
                                     @Param("statuses") Collection<SurveyStatus> statuses,
                                     @Param("excludeId") Integer excludeId);

    // Lịch khảo sát của nhân viên trong khoảng ngày, sắp theo ngày rồi giờ bắt đầu
    @Query("""
            SELECT s FROM BookingSurvey s JOIN FETCH s.timeSlot ts
            WHERE s.surveyStaff.id = :staffId
              AND s.surveyDate BETWEEN :fromDate AND :toDate
              AND s.status IN :statuses
            ORDER BY s.surveyDate ASC, ts.startTime ASC
            """)
    List<BookingSurvey> findByStaffAndDateRange(@Param("staffId") Long staffId,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate,
                                                @Param("statuses") Collection<SurveyStatus> statuses);

    // Khóa dòng (SELECT ... FOR UPDATE) đến hết transaction để hai thao tác đổi trạng thái không ghi đè nhau
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM BookingSurvey s WHERE s.id = :id")
    Optional<BookingSurvey> findByIdForUpdate(@Param("id") Integer id);
}
