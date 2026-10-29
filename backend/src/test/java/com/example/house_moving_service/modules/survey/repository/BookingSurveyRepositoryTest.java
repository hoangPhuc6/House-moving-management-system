package com.example.house_moving_service.modules.survey.repository;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.repository.StaffRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.survey.dto.request.SurveyFilterRequest;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.specification.BookingSurveySpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BookingSurveyRepositoryTest {

    // Ngày xa trong tương lai để không đụng dữ liệu nhập tay trên DB dev
    private static final LocalDate DAY = LocalDate.of(2099, 1, 5);

    @Autowired
    private BookingSurveyRepository bookingSurveyRepository;
    @Autowired
    private SurveyTimeSlotRepository surveyTimeSlotRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StaffRepository staffRepository;

    private Booking booking1;
    private Booking booking2;
    private Staff mover1;
    private Staff mover2;
    private SurveyTimeSlot slot0800;
    private SurveyTimeSlot slot1000;

    @BeforeEach
    void setUp() {
        // 2 booking seed V9; xóa khảo sát cũ của chúng (nếu có) để test độc lập, mọi thứ rollback sau test
        List<Booking> bookings = bookingRepository.findAll(PageRequest.of(0, 2)).getContent();
        booking1 = bookings.get(0);
        booking2 = bookings.get(1);
        bookingSurveyRepository.deleteAll(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(booking1.getId()));
        bookingSurveyRepository.deleteAll(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(booking2.getId()));
        bookingSurveyRepository.flush();

        mover1 = staffRepository.getReferenceById(userRepository.findByEmail("mover1@housemoving.vn").orElseThrow().getId());
        mover2 = staffRepository.getReferenceById(userRepository.findByEmail("mover2@housemoving.vn").orElseThrow().getId());

        // Khung giờ seed V12
        List<SurveyTimeSlot> slots = surveyTimeSlotRepository.findAllByOrderByStartTimeAsc();
        slot0800 = slots.get(0);
        slot1000 = slots.get(1);
    }

    private BookingSurvey survey(Booking booking, SurveyStatus status, Staff staff, LocalDate date, SurveyTimeSlot slot) {
        return BookingSurvey.builder()
                .booking(booking)
                .surveyMethod(SurveyMethod.ONSITE)
                .status(status)
                .surveyStaff(staff)
                .surveyDate(date)
                .timeSlot(slot)
                .build();
    }

    // ---------- Ràng buộc DB ----------

    @Test
    @DisplayName("Lưu khảo sát CONFIRMED với nhân viên seed: FK survey_staff_id -> staff(user_id) hợp lệ, status mặc định PENDING khi không truyền")
    void testSave_ValidSurvey() {
        BookingSurvey saved = bookingSurveyRepository.saveAndFlush(
                survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot0800));
        BookingSurvey pending = bookingSurveyRepository.saveAndFlush(BookingSurvey.builder()
                .booking(booking2).surveyMethod(SurveyMethod.PHONE).build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(pending.getStatus()).isEqualTo(SurveyStatus.PENDING);
    }

    @Test
    @DisplayName("Nhân viên không có hồ sơ staff (customer) vi phạm fk_booking_surveys_staff")
    void testSave_NonStaffUser_ViolatesFk() {
        Long customerUserId = userRepository.findByEmail("customer1@gmail.com").orElseThrow().getId();
        // Reference (proxy) mang id của customer: insert sẽ ghi survey_staff_id = id đó, không có dòng staff tương ứng
        Staff notStaff = staffRepository.getReferenceById(customerUserId);

        assertThatThrownBy(() -> bookingSurveyRepository.saveAndFlush(
                survey(booking1, SurveyStatus.CONFIRMED, notStaff, DAY, slot0800)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_booking_surveys_staff");
    }

    @Test
    @DisplayName("2 khảo sát đang hoạt động cùng booking vi phạm uq_booking_surveys_one_active_per_booking")
    void testTwoActiveSurveysSameBooking_Violation() {
        bookingSurveyRepository.saveAndFlush(survey(booking1, SurveyStatus.PENDING, null, null, null));

        assertThatThrownBy(() -> bookingSurveyRepository.saveAndFlush(
                survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot0800)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_booking_surveys_one_active_per_booking");
    }

    @Test
    @DisplayName("1 khảo sát đang hoạt động + nhiều khảo sát đã kết thúc cùng booking là hợp lệ")
    void testActivePlusFinishedSurveysSameBooking_Allowed() {
        bookingSurveyRepository.saveAndFlush(survey(booking1, SurveyStatus.CANCELLED, null, null, null));
        bookingSurveyRepository.saveAndFlush(survey(booking1, SurveyStatus.RESURVEY_REQUIRED, mover1, DAY, slot0800));
        bookingSurveyRepository.saveAndFlush(survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY.plusDays(1), slot0800));

        assertThat(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(booking1.getId())).hasSize(3);
    }

    @Test
    @DisplayName("Cùng nhân viên / ngày / khung giờ, cả hai CONFIRMED vi phạm uq_booking_surveys_staff_slot")
    void testSameStaffSlot_Violation() {
        bookingSurveyRepository.saveAndFlush(survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot0800));

        assertThatThrownBy(() -> bookingSurveyRepository.saveAndFlush(
                survey(booking2, SurveyStatus.CONFIRMED, mover1, DAY, slot0800)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_booking_surveys_staff_slot");
    }

    @Test
    @DisplayName("Cùng nhân viên / ngày / khung giờ nhưng một lịch đã CANCELLED là hợp lệ")
    void testSameStaffSlot_OneCancelled_Allowed() {
        bookingSurveyRepository.saveAndFlush(survey(booking1, SurveyStatus.CANCELLED, mover1, DAY, slot0800));
        BookingSurvey confirmed = bookingSurveyRepository.saveAndFlush(
                survey(booking2, SurveyStatus.CONFIRMED, mover1, DAY, slot0800));

        assertThat(confirmed.getId()).isNotNull();
    }

    @Test
    @DisplayName("CONFIRMED thiếu nhân viên vi phạm chk_booking_surveys_scheduled_fields")
    void testConfirmedWithoutStaff_ViolatesCheck() {
        assertThatThrownBy(() -> bookingSurveyRepository.saveAndFlush(
                survey(booking1, SurveyStatus.CONFIRMED, null, DAY, slot0800)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_booking_surveys_scheduled_fields");
    }

    @Test
    @DisplayName("actual_end_at trước actual_start_at vi phạm chk_booking_surveys_actual_range")
    void testActualEndBeforeStart_ViolatesCheck() {
        BookingSurvey s = survey(booking1, SurveyStatus.COMPLETED, mover1, DAY, slot0800);
        s.setActualStartAt(LocalDateTime.of(2099, 1, 5, 9, 0));
        s.setActualEndAt(LocalDateTime.of(2099, 1, 5, 8, 0));

        assertThatThrownBy(() -> bookingSurveyRepository.saveAndFlush(s))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_booking_surveys_actual_range");
    }

    // ---------- Query ----------

    @Test
    @DisplayName("existsSurveySlotConflict: tạo mới (excludeId = null) phát hiện được lịch trùng; đổi lịch bỏ qua chính nó")
    void testExistsSurveySlotConflict_CreateAndReschedule() {
        BookingSurvey confirmed = bookingSurveyRepository.saveAndFlush(
                survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot0800));
        EnumSet<SurveyStatus> occupying = EnumSet.copyOf(SurveyStatus.OCCUPYING_SLOT);

        // Tạo lịch mới trùng giờ: phải phát hiện (hàm sinh từ tên ...AndIdNot(null) sẽ trả false ở đây)
        assertThat(bookingSurveyRepository.existsSurveySlotConflict(
                mover1.getId(), DAY, slot0800.getId(), occupying, null)).isTrue();
        // Đổi lịch chính nó sang cùng khung giờ: không tính là trùng
        assertThat(bookingSurveyRepository.existsSurveySlotConflict(
                mover1.getId(), DAY, slot0800.getId(), occupying, confirmed.getId())).isFalse();
    }

    @Test
    @DisplayName("existsSurveySlotConflict: khác ngày / khác khung giờ / khác nhân viên / lịch đã hủy đều không trùng")
    void testExistsSurveySlotConflict_NoConflict() {
        bookingSurveyRepository.saveAndFlush(survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot0800));
        bookingSurveyRepository.saveAndFlush(survey(booking2, SurveyStatus.CANCELLED, mover1, DAY, slot1000));
        EnumSet<SurveyStatus> occupying = EnumSet.copyOf(SurveyStatus.OCCUPYING_SLOT);

        assertThat(bookingSurveyRepository.existsSurveySlotConflict(
                mover1.getId(), DAY.plusDays(1), slot0800.getId(), occupying, null)).isFalse();
        assertThat(bookingSurveyRepository.existsSurveySlotConflict(
                mover1.getId(), DAY, slot1000.getId(), occupying, null)).isFalse();
        assertThat(bookingSurveyRepository.existsSurveySlotConflict(
                mover2.getId(), DAY, slot0800.getId(), occupying, null)).isFalse();
    }

    @Test
    @DisplayName("findByStaffAndDateRange: chỉ trả lịch trong khoảng ngày, đúng trạng thái, sắp theo ngày rồi giờ")
    void testFindByStaffAndDateRange() {
        BookingSurvey morning = bookingSurveyRepository.save(survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot0800));
        BookingSurvey later = bookingSurveyRepository.save(survey(booking1, SurveyStatus.COMPLETED, mover1, DAY, slot1000));
        bookingSurveyRepository.save(survey(booking2, SurveyStatus.CANCELLED, mover1, DAY.plusDays(1), slot0800));
        bookingSurveyRepository.save(survey(booking2, SurveyStatus.CONFIRMED, mover1, DAY.plusDays(5), slot0800));
        bookingSurveyRepository.flush();

        List<BookingSurvey> result = bookingSurveyRepository.findByStaffAndDateRange(
                mover1.getId(), DAY, DAY.plusDays(1),
                EnumSet.of(SurveyStatus.CONFIRMED, SurveyStatus.IN_PROGRESS, SurveyStatus.COMPLETED));

        assertThat(result).extracting(BookingSurvey::getId).containsExactly(morning.getId(), later.getId());
        assertThat(result.get(0).getTimeSlot().getStartTime()).isEqualTo(slot0800.getStartTime());
    }

    @Test
    @DisplayName("findFirstByBookingIdAndStatusIn / findTopByBookingIdOrderByCreatedAtDesc / findByIdForUpdate")
    void testFindActiveLatestAndLock() {
        bookingSurveyRepository.saveAndFlush(survey(booking1, SurveyStatus.CANCELLED, null, null, null));
        BookingSurvey active = bookingSurveyRepository.saveAndFlush(
                survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot0800));

        assertThat(bookingSurveyRepository.findFirstByBookingIdAndStatusIn(booking1.getId(), SurveyStatus.ACTIVE))
                .get().extracting(BookingSurvey::getId).isEqualTo(active.getId());
        assertThat(bookingSurveyRepository.findFirstByBookingIdAndStatusIn(booking2.getId(), SurveyStatus.ACTIVE))
                .isEmpty();
        assertThat(bookingSurveyRepository.findTopByBookingIdOrderByCreatedAtDesc(booking1.getId()))
                .isPresent();
        assertThat(bookingSurveyRepository.findByIdForUpdate(active.getId()))
                .get().extracting(BookingSurvey::getStatus).isEqualTo(SurveyStatus.CONFIRMED);
    }

    @Test
    @DisplayName("BookingSurveySpecification: lọc theo nhân viên + mã đơn + khoảng ngày, phân trang (query lấy dữ liệu và query đếm)")
    void testSpecification_FilterAndPaginate() {
        bookingSurveyRepository.save(survey(booking1, SurveyStatus.CONFIRMED, mover1, DAY, slot0800));
        bookingSurveyRepository.save(survey(booking2, SurveyStatus.CONFIRMED, mover2, DAY, slot0800));
        bookingSurveyRepository.flush();

        SurveyFilterRequest filter = SurveyFilterRequest.builder()
                .staffId(mover1.getId())
                .bookingCode(booking1.getBookingCode().toLowerCase())
                .dateFrom(DAY)
                .dateTo(DAY)
                .status(SurveyStatus.CONFIRMED)
                .method(SurveyMethod.ONSITE)
                .build();

        Page<BookingSurvey> page = bookingSurveyRepository.findAll(
                BookingSurveySpecification.filterSurveys(filter), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement()
                .extracting(s -> s.getBooking().getId()).isEqualTo(booking1.getId());
    }
}
