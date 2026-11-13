package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingHistoryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingScheduleResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingTripResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.ManagementMovingHistoryResponse;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleVehicleAssignment;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.MovingScheduleMapper;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.service.MovingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovingHistoryServiceImpl implements MovingHistoryService {

    private final MovingScheduleRepository movingScheduleRepository;
    private final ScheduleStaffAssignmentRepository staffAssignmentRepository;
    private final ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final MovingScheduleMapper movingScheduleMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerMovingScheduleResponse> getCustomerSchedules(Integer bookingId, Long customerUserId) {
        requireOwner(bookingId, customerUserId);
        return visibleToCustomer(bookingId).stream()
                .map(movingScheduleMapper::toCustomerSchedule)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerMovingTripResponse getCustomerSchedule(Integer bookingId, Integer scheduleId, Long customerUserId) {
        requireOwner(bookingId, customerUserId);
        MovingSchedule schedule = movingScheduleRepository.findById(scheduleId)
                .filter(s -> s.getBooking().getId().equals(bookingId))
                .filter(s -> s.getStatus() != MovingScheduleStatus.CANCELLED)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, scheduleId));
        List<Integer> ids = List.of(scheduleId);
        return movingScheduleMapper.toCustomerTrip(schedule,
                staffAssignmentRepository.findAllWithStaffByScheduleIds(ids),
                vehicleAssignmentRepository.findAllWithVehicleByScheduleIds(ids));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerMovingHistoryResponse getCustomerHistory(Integer bookingId, Long customerUserId) {
        BookingSnapshot booking = requireOwner(bookingId, customerUserId);
        List<MovingSchedule> schedules = visibleToCustomer(bookingId);
        Assignments assignments = loadAssignments(schedules);
        List<CustomerMovingTripResponse> trips = schedules.stream()
                .map(s -> movingScheduleMapper.toCustomerTrip(s, assignments.staffOf(s), assignments.vehiclesOf(s)))
                .toList();
        // TODO Task 2 / Task 3: bổ sung sự cố và phụ phí của đơn
        return CustomerMovingHistoryResponse.builder()
                .bookingId(booking.id())
                .bookingCode(booking.bookingCode())
                .bookingStatus(booking.status())
                .trips(trips)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagementMovingHistoryResponse getManagementHistory(Integer bookingId) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        List<MovingSchedule> schedules = movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(bookingId);
        Assignments assignments = loadAssignments(schedules);
        return ManagementMovingHistoryResponse.builder()
                .bookingId(booking.id())
                .bookingCode(booking.bookingCode())
                .bookingStatus(booking.status())
                .trips(schedules.stream()
                        .map(s -> movingScheduleMapper.toManagementTrip(s, assignments.staffOf(s), assignments.vehiclesOf(s)))
                        .toList())
                .build();
    }

    // Đơn tồn tại (ERR_BOOKING_NOT_FOUND) và thuộc người gọi (ERR_BOOKING_FORBIDDEN)
    private BookingSnapshot requireOwner(Integer bookingId, Long customerUserId) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        if (booking.customerUserId() == null || !booking.customerUserId().equals(customerUserId)) {
            throw new AppException(ErrorCode.ERR_BOOKING_FORBIDDEN);
        }
        return booking;
    }

    private List<MovingSchedule> visibleToCustomer(Integer bookingId) {
        return movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(bookingId).stream()
                .filter(s -> s.getStatus() != MovingScheduleStatus.CANCELLED)
                .toList();
    }

    // Nạp phân công của mọi chuyến trong 2 truy vấn rồi nhóm theo chuyến
    private Assignments loadAssignments(List<MovingSchedule> schedules) {
        if (schedules.isEmpty()) {
            return new Assignments(Map.of(), Map.of());
        }
        List<Integer> ids = schedules.stream().map(MovingSchedule::getId).toList();
        return new Assignments(
                staffAssignmentRepository.findAllWithStaffByScheduleIds(ids).stream()
                        .collect(Collectors.groupingBy(a -> a.getMovingSchedule().getId())),
                vehicleAssignmentRepository.findAllWithVehicleByScheduleIds(ids).stream()
                        .collect(Collectors.groupingBy(a -> a.getMovingSchedule().getId())));
    }

    private record Assignments(Map<Integer, List<ScheduleStaffAssignment>> staff,
                               Map<Integer, List<ScheduleVehicleAssignment>> vehicles) {

        List<ScheduleStaffAssignment> staffOf(MovingSchedule schedule) {
            return staff.getOrDefault(schedule.getId(), List.of());
        }

        List<ScheduleVehicleAssignment> vehiclesOf(MovingSchedule schedule) {
            return vehicles.getOrDefault(schedule.getId(), List.of());
        }
    }
}
