package com.example.house_moving_service.modules.scheduling.mapper;

import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingScheduleResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingTripResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.FieldMovingScheduleResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.ManagementMovingTripResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingStaffResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingVehicleResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SupervisorContact;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleVehicleAssignment;
import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.example.house_moving_service.modules.scheduling.helper.MovingLogParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class MovingScheduleMapper {

    public MovingStaffResponse toStaffResponse(ScheduleStaffAssignment assignment) {
        Staff staff = assignment.getStaff();
        return MovingStaffResponse.builder()
                .assignmentId(assignment.getId())
                .userId(staff.getId())
                .staffCode(staff.getStaffCode())
                .fullName(staff.getFullName())
                .phoneNumber(staff.getUser() != null ? staff.getUser().getPhoneNumber() : null)
                .role(assignment.getRole())
                .status(assignment.getStatus())
                .build();
    }

    public MovingVehicleResponse toVehicleResponse(ScheduleVehicleAssignment assignment) {
        Vehicle vehicle = assignment.getVehicle();
        return MovingVehicleResponse.builder()
                .assignmentId(assignment.getId())
                .vehicleId(vehicle.getId())
                .licensePlate(vehicle.getLicensePlate())
                .vehicleTypeName(vehicle.getVehicleType() != null ? vehicle.getVehicleType().getName() : null)
                .capacityM3(vehicle.getCapacityM3())
                .vehicleStatus(vehicle.getStatus())
                .status(assignment.getStatus())
                .build();
    }

    /** staff / vehicles truyền vào là dòng phân công chưa hủy của chuyến. */
    public FieldMovingScheduleResponse toFieldResponse(MovingSchedule schedule, BookingSnapshot booking, BookingRouteResponse route,
                                                       StaffAssignmentRole myRole,
                                                       List<ScheduleStaffAssignment> staff,
                                                       List<ScheduleVehicleAssignment> vehicles,
                                                       BigDecimal uncollectedSurchargeTotal) {
        return FieldMovingScheduleResponse.builder()
                .id(schedule.getId())
                .bookingId(booking.id())
                .bookingCode(booking.bookingCode())
                .bookingStatus(booking.status())
                .status(schedule.getStatus())
                .scheduledStartAt(schedule.getScheduledStartAt())
                .scheduledEndAt(schedule.getScheduledEndAt())
                .actualStartAt(schedule.getActualStartAt())
                .actualEndAt(schedule.getActualEndAt())
                .delayMinutes(MovingLogParser.delayMinutes(schedule))
                .myRole(myRole)
                .contactName(booking.contactName())
                .contactPhone(booking.contactPhone())
                .route(route)
                .staff(staff.stream().map(this::toStaffResponse).toList())
                .vehicles(vehicles.stream().map(this::toVehicleResponse).toList())
                .logs(MovingLogParser.parse(schedule.getNote()))
                .uncollectedTotal(uncollectedSurchargeTotal)
                .build();
    }

    public CustomerMovingScheduleResponse toCustomerSchedule(MovingSchedule schedule) {
        return CustomerMovingScheduleResponse.builder()
                .id(schedule.getId())
                .status(schedule.getStatus())
                .scheduledStartAt(schedule.getScheduledStartAt())
                .scheduledEndAt(schedule.getScheduledEndAt())
                .actualStartAt(schedule.getActualStartAt())
                .actualEndAt(schedule.getActualEndAt())
                .delayMinutes(MovingLogParser.delayMinutes(schedule))
                .build();
    }

    /**
     * Chỉ thông tin khách cần biết: dòng thời gian (tên mốc + thời điểm), biển số xe, họ tên và số điện thoại giám sát viên.
     * Bỏ qua phân công / xe đã hủy; không trả ghi chú tự do hay id nhân viên.
     */
    public CustomerMovingTripResponse toCustomerTrip(MovingSchedule schedule,
                                                     List<ScheduleStaffAssignment> staff,
                                                     List<ScheduleVehicleAssignment> vehicles) {
        SupervisorContact supervisor = staff.stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED && a.getRole() == StaffAssignmentRole.SUPERVISOR)
                .findFirst()
                .map(a -> new SupervisorContact(a.getStaff().getFullName(),
                        a.getStaff().getUser() != null ? a.getStaff().getUser().getPhoneNumber() : null))
                .orElse(null);
        List<String> plates = vehicles.stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .map(a -> a.getVehicle().getLicensePlate())
                .toList();
        return CustomerMovingTripResponse.builder()
                .id(schedule.getId())
                .status(schedule.getStatus())
                .scheduledStartAt(schedule.getScheduledStartAt())
                .scheduledEndAt(schedule.getScheduledEndAt())
                .actualStartAt(schedule.getActualStartAt())
                .actualEndAt(schedule.getActualEndAt())
                .delayMinutes(MovingLogParser.delayMinutes(schedule))
                .timeline(MovingLogParser.timeline(schedule.getNote()))
                .licensePlates(plates)
                .supervisor(supervisor)
                .build();
    }

    /** Đầy đủ cho điều phối: nhật ký gốc và đã tách, mọi phân công / xe kể cả đã hủy. */
    public ManagementMovingTripResponse toManagementTrip(MovingSchedule schedule,
                                                         List<ScheduleStaffAssignment> staff,
                                                         List<ScheduleVehicleAssignment> vehicles) {
        return ManagementMovingTripResponse.builder()
                .id(schedule.getId())
                .status(schedule.getStatus())
                .scheduledStartAt(schedule.getScheduledStartAt())
                .scheduledEndAt(schedule.getScheduledEndAt())
                .actualStartAt(schedule.getActualStartAt())
                .actualEndAt(schedule.getActualEndAt())
                .delayMinutes(MovingLogParser.delayMinutes(schedule))
                .note(schedule.getNote())
                .logs(MovingLogParser.parse(schedule.getNote()))
                .staff(staff.stream().map(this::toStaffResponse).toList())
                .vehicles(vehicles.stream().map(this::toVehicleResponse).toList())
                .createdBy(schedule.getCreatedBy() != null ? schedule.getCreatedBy().getId() : null)
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
