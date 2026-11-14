package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateVehicleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleStatusRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import com.example.house_moving_service.modules.scheduling.entity.VehicleType;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.VehicleMapper;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private VehicleTypeRepository vehicleTypeRepository;
    @Mock
    private ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;

    private VehicleServiceImpl vehicleService;

    private VehicleType truck;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleServiceImpl(vehicleRepository, vehicleTypeRepository,
                vehicleAssignmentRepository, new VehicleMapper());
        truck = VehicleType.builder().id(1).name("Xe tải 1.25 tấn")
                .maxLoadKg(new BigDecimal("1250")).maxVolumeM3(new BigDecimal("8.00")).isActive(true).build();
    }

    private Vehicle vehicle(VehicleStatus status) {
        return Vehicle.builder().id(10).licensePlate("51C-123.45").vehicleType(truck)
                .capacityM3(new BigDecimal("8.00")).status(status).build();
    }

    private void saveReturnsArgument() {
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(10);
            }
            return v;
        });
    }

    @Test
    @DisplayName("create: chuẩn hóa biển số, lấy thể tích theo loại xe, mặc định AVAILABLE")
    void create_success() {
        when(vehicleRepository.existsByLicensePlateIgnoreCase("51C-123.45")).thenReturn(false);
        when(vehicleTypeRepository.findById(1)).thenReturn(Optional.of(truck));
        saveReturnsArgument();

        VehicleResponse res = vehicleService.create(CreateVehicleRequest.builder()
                .licensePlate("  51c-123.45 ").vehicleTypeId(1).build());

        assertEquals("51C-123.45", res.licensePlate());
        assertEquals(new BigDecimal("8.00"), res.capacityM3());
        assertEquals(VehicleStatus.AVAILABLE, res.status());
        assertEquals("Xe tải 1.25 tấn", res.vehicleTypeName());
    }

    @Test
    @DisplayName("create: trùng biển số → ERR_DUPLICATE_LICENSE_PLATE")
    void create_duplicatePlate() {
        when(vehicleRepository.existsByLicensePlateIgnoreCase("51C-123.45")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> vehicleService.create(
                CreateVehicleRequest.builder().licensePlate("51c-123.45").vehicleTypeId(1).build()));

        assertEquals(ErrorCode.ERR_DUPLICATE_LICENSE_PLATE, ex.getErrorCode());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: loại xe đã ngừng hoạt động → ERR_VEHICLE_TYPE_INACTIVE")
    void create_inactiveType() {
        truck.setIsActive(false);
        when(vehicleRepository.existsByLicensePlateIgnoreCase(any())).thenReturn(false);
        when(vehicleTypeRepository.findById(1)).thenReturn(Optional.of(truck));

        AppException ex = assertThrows(AppException.class, () -> vehicleService.create(
                CreateVehicleRequest.builder().licensePlate("51C-123.45").vehicleTypeId(1).build()));

        assertEquals(ErrorCode.ERR_VEHICLE_TYPE_INACTIVE, ex.getErrorCode());
    }

    @Test
    @DisplayName("create: không cho tạo xe ở trạng thái IN_USE")
    void create_inUseRejected() {
        when(vehicleRepository.existsByLicensePlateIgnoreCase(any())).thenReturn(false);
        when(vehicleTypeRepository.findById(1)).thenReturn(Optional.of(truck));

        AppException ex = assertThrows(AppException.class, () -> vehicleService.create(
                CreateVehicleRequest.builder().licensePlate("51C-123.45").vehicleTypeId(1)
                        .status(VehicleStatus.IN_USE).build()));

        assertEquals(ErrorCode.ERR_VEHICLE_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("update: biển số trùng với xe khác → ERR_DUPLICATE_LICENSE_PLATE")
    void update_duplicatePlate() {
        when(vehicleRepository.findWithTypeById(10)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
        when(vehicleRepository.existsByLicensePlateIgnoreCaseAndIdNot("30A-999.99", 10)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> vehicleService.update(10,
                UpdateVehicleRequest.builder().licensePlate("30a-999.99").vehicleTypeId(1).build()));

        assertEquals(ErrorCode.ERR_DUPLICATE_LICENSE_PLATE, ex.getErrorCode());
    }

    @Test
    @DisplayName("update: giữ nguyên loại xe đã ngừng hoạt động vẫn cập nhật được")
    void update_keepInactiveType() {
        truck.setIsActive(false);
        when(vehicleRepository.findWithTypeById(10)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
        when(vehicleRepository.existsByLicensePlateIgnoreCaseAndIdNot("51C-123.45", 10)).thenReturn(false);
        saveReturnsArgument();

        VehicleResponse res = vehicleService.update(10, UpdateVehicleRequest.builder()
                .licensePlate("51C-123.45").vehicleTypeId(1).capacityM3(new BigDecimal("7.5")).note("Thùng kín").build());

        assertEquals(new BigDecimal("7.5"), res.capacityM3());
        assertEquals("Thùng kín", res.note());
        verify(vehicleTypeRepository, never()).findById(any());
    }

    @Test
    @DisplayName("updateStatus: không đặt tay IN_USE")
    void updateStatus_inUseRejected() {
        when(vehicleRepository.findWithTypeById(10)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));

        AppException ex = assertThrows(AppException.class, () -> vehicleService.updateStatus(10,
                UpdateVehicleStatusRequest.builder().status(VehicleStatus.IN_USE).build()));

        assertEquals(ErrorCode.ERR_VEHICLE_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateStatus: xe đang chạy chuyến → ERR_VEHICLE_BUSY")
    void updateStatus_onTrip() {
        when(vehicleRepository.findWithTypeById(10)).thenReturn(Optional.of(vehicle(VehicleStatus.IN_USE)));
        when(vehicleAssignmentRepository.isOnTripInProgress(10)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> vehicleService.updateStatus(10,
                UpdateVehicleStatusRequest.builder().status(VehicleStatus.AVAILABLE).build()));

        assertEquals(ErrorCode.ERR_VEHICLE_BUSY, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateStatus: chuyển MAINTENANCE khi còn chuyến chưa xong → ERR_VEHICLE_BUSY")
    void updateStatus_maintenanceWithUpcomingTrip() {
        when(vehicleRepository.findWithTypeById(10)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
        when(vehicleAssignmentRepository.isOnTripInProgress(10)).thenReturn(false);
        when(vehicleAssignmentRepository.hasUnfinishedAssignment(10)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> vehicleService.updateStatus(10,
                UpdateVehicleStatusRequest.builder().status(VehicleStatus.MAINTENANCE).build()));

        assertEquals(ErrorCode.ERR_VEHICLE_BUSY, ex.getErrorCode());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus: xe rảnh chuyển MAINTENANCE thành công, ghi đè ghi chú")
    void updateStatus_maintenanceSuccess() {
        when(vehicleRepository.findWithTypeById(10)).thenReturn(Optional.of(vehicle(VehicleStatus.AVAILABLE)));
        when(vehicleAssignmentRepository.isOnTripInProgress(10)).thenReturn(false);
        when(vehicleAssignmentRepository.hasUnfinishedAssignment(10)).thenReturn(false);
        saveReturnsArgument();

        VehicleResponse res = vehicleService.updateStatus(10, UpdateVehicleStatusRequest.builder()
                .status(VehicleStatus.MAINTENANCE).note("Thay lốp").build());

        assertEquals(VehicleStatus.MAINTENANCE, res.status());
        assertEquals("Thay lốp", res.note());
    }

    @Test
    @DisplayName("softDelete: chuyển xe sang INACTIVE")
    void softDelete_success() {
        Vehicle v = vehicle(VehicleStatus.AVAILABLE);
        when(vehicleRepository.findWithTypeById(10)).thenReturn(Optional.of(v));
        when(vehicleAssignmentRepository.isOnTripInProgress(10)).thenReturn(false);
        when(vehicleAssignmentRepository.hasUnfinishedAssignment(10)).thenReturn(false);

        vehicleService.softDelete(10);

        assertEquals(VehicleStatus.INACTIVE, v.getStatus());
        verify(vehicleRepository).save(v);
    }

    @Test
    @DisplayName("getById: không tồn tại → ERR_VEHICLE_NOT_FOUND")
    void getById_notFound() {
        when(vehicleRepository.findWithTypeById(99)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> vehicleService.getById(99));

        assertEquals(ErrorCode.ERR_VEHICLE_NOT_FOUND, ex.getErrorCode());
    }
}
