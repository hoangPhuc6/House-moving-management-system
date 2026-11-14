package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.scheduling.dto.request.AvailabilityQuery;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingStaffAvailabilityResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import com.example.house_moving_service.modules.scheduling.entity.VehicleType;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.MovingScheduleMapper;
import com.example.house_moving_service.modules.scheduling.mapper.VehicleMapper;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleRepository;
import com.example.house_moving_service.modules.survey.service.SurveyStaffScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovingScheduleQueryServiceImplTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 20, 8, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 20, 12, 0);

    @Mock
    private MovingScheduleRepository movingScheduleRepository;
    @Mock
    private ScheduleStaffAssignmentRepository staffAssignmentRepository;
    @Mock
    private ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private StaffLookupService staffLookupService;
    @Mock
    private SurveyStaffScheduleService surveyStaffScheduleService;

    private MovingScheduleQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MovingScheduleQueryServiceImpl(movingScheduleRepository, staffAssignmentRepository,
                vehicleAssignmentRepository, vehicleRepository, staffLookupService, surveyStaffScheduleService,
                new MovingScheduleMapper(), new VehicleMapper());
    }

    private static AvailabilityQuery query() {
        return AvailabilityQuery.builder().start(START).end(END).build();
    }

    private static Vehicle vehicle(int id, int typeId) {
        return Vehicle.builder().id(id).licensePlate("51C-00" + id).status(VehicleStatus.AVAILABLE)
                .vehicleType(VehicleType.builder().id(typeId).name("Loại " + typeId).isActive(true).build()).build();
    }

    @Test
    @DisplayName("Thiếu start / end → ERR_MOVING_SCHEDULE_INVALID_TIME")
    void missingTime() {
        AppException ex = assertThrows(AppException.class,
                () -> service.findAvailableVehicles(AvailabilityQuery.builder().start(START).build()));

        assertEquals(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, ex.getErrorCode());
    }

    @Test
    @DisplayName("end không sau start → ERR_MOVING_SCHEDULE_INVALID_TIME")
    void endBeforeStart() {
        AppException ex = assertThrows(AppException.class,
                () -> service.findStaffAvailability(AvailabilityQuery.builder().start(END).end(START).build()));

        assertEquals(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xe rảnh: lọc theo loại xe, không truyền excludeScheduleId thì dùng 0")
    void availableVehicles_filterByType() {
        when(vehicleRepository.findAvailable(eq(START), eq(END), eq(0), any(), any(), any()))
                .thenReturn(List.of(vehicle(1, 1), vehicle(2, 2)));

        List<VehicleResponse> res = service.findAvailableVehicles(
                AvailabilityQuery.builder().start(START).end(END).vehicleTypeId(2).build());

        assertEquals(1, res.size());
        assertEquals(2, res.get(0).id());
    }

    @Test
    @DisplayName("Nhân viên: đánh dấu bận chuyến (MOVING), bận khảo sát (SURVEY), rảnh")
    void staffAvailability() {
        when(staffLookupService.findActiveByRole(RoleCode.MOVER)).thenReturn(List.of(
                new StaffSummary(3L, "STF-003", "Lê Hoàng Nhân Công", "mover1@housemoving.vn", "ACTIVE"),
                new StaffSummary(4L, "STF-004", "Phạm Minh Tài Xế", "mover2@housemoving.vn", "ACTIVE"),
                new StaffSummary(5L, "STF-005", "Nguyễn Văn Rảnh", "mover3@housemoving.vn", "ACTIVE")));
        when(staffAssignmentRepository.findConflictingStaffIds(anyCollection(), eq(START), eq(END), eq(0)))
                .thenReturn(List.of(3L));
        when(surveyStaffScheduleService.hasSurveyBetween(4L, START, END)).thenReturn(true);
        when(surveyStaffScheduleService.hasSurveyBetween(5L, START, END)).thenReturn(false);

        List<MovingStaffAvailabilityResponse> res = service.findStaffAvailability(query());

        assertEquals(MovingStaffAvailabilityResponse.BUSY_MOVING, res.get(0).busyReason());
        assertFalse(res.get(0).available());
        assertEquals(MovingStaffAvailabilityResponse.BUSY_SURVEY, res.get(1).busyReason());
        assertTrue(res.get(2).available());
        assertNull(res.get(2).busyReason());
    }

    @Test
    @DisplayName("Không có nhân viên hiện trường → danh sách rỗng, không truy vấn trùng lịch")
    void noMovers() {
        when(staffLookupService.findActiveByRole(RoleCode.MOVER)).thenReturn(List.of());

        assertTrue(service.findStaffAvailability(query()).isEmpty());
    }
}
