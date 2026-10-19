package com.example.house_moving_service.service;

import com.example.house_moving_service.config.MapApiProperties;
import com.example.house_moving_service.config.PricingProperties;
import com.example.house_moving_service.dto.request.pricing.LocationDto;
import com.example.house_moving_service.dto.request.pricing.PricingCalculationRequest;
import com.example.house_moving_service.dto.request.pricing.RouteDto;
import com.example.house_moving_service.dto.request.pricing.ScheduleDto;
import com.example.house_moving_service.dto.request.pricing.ServiceItemDto;
import com.example.house_moving_service.dto.response.pricing.PricingCalculationResponse;
import com.example.house_moving_service.entity.Service;
import com.example.house_moving_service.entity.ServiceType;
import com.example.house_moving_service.entity.Voucher;
import com.example.house_moving_service.enums.DiscountType;
import com.example.house_moving_service.enums.PropertyType;
import com.example.house_moving_service.enums.ServiceStatus;
import com.example.house_moving_service.exception.AppException;
import com.example.house_moving_service.exception.ErrorCode;
import com.example.house_moving_service.repository.ServiceRepository;
import com.example.house_moving_service.repository.VoucherRepository;
import com.example.house_moving_service.service.helper.RouteDistanceHelper;
import com.example.house_moving_service.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private VoucherRepository voucherRepository;

    private PricingProperties pricingProperties;
    private MapApiProperties mapApiProperties;
    private RouteDistanceHelper routeDistanceHelper;
    private PricingService pricingService;

    private Service truckService;
    private Service laborService;
    private Service technicalService;
    private Service materialService;
    private Service terrainService;

    @BeforeEach
    void setUp() {
        pricingProperties = new PricingProperties();
        pricingProperties.setMinLeadHours(2);
        pricingProperties.setBaseTransportIncludedKm(new BigDecimal("10.0"));
        pricingProperties.setHaversineWindingFactor(1.3);

        mapApiProperties = new MapApiProperties();
        mapApiProperties.setEnabled(false);

        routeDistanceHelper = new RouteDistanceHelper(pricingProperties, mapApiProperties);
        pricingService = new PricingServiceImpl(pricingProperties, routeDistanceHelper, serviceRepository, voucherRepository);

        ServiceType typeTransport = ServiceType.builder().id(1).name("Xe tải vận chuyển").isActive(true).build();
        ServiceType typeLabor = ServiceType.builder().id(2).name("Bốc xếp & Khuân vác").isActive(true).build();
        ServiceType typeTechnical = ServiceType.builder().id(3).name("Dịch vụ kỹ thuật").isActive(true).build();
        ServiceType typeMaterial = ServiceType.builder().id(4).name("Vật tư đóng gói").isActive(true).build();
        ServiceType typeTerrain = ServiceType.builder().id(5).name("Phụ phí địa hình").isActive(true).build();

        truckService = Service.builder()
                .id(1).serviceType(typeTransport).name("Xe tải 1.5 Tấn (Nội thành)")
                .basePrice(new BigDecimal("450000.00")).status(ServiceStatus.ACTIVE).build();

        laborService = Service.builder()
                .id(2).serviceType(typeLabor).name("Nhân công bốc xếp cơ bản")
                .basePrice(new BigDecimal("250000.00")).status(ServiceStatus.ACTIVE).build();

        technicalService = Service.builder()
                .id(3).serviceType(typeTechnical).name("Tháo lắp máy lạnh")
                .basePrice(new BigDecimal("300000.00")).status(ServiceStatus.ACTIVE).build();

        materialService = Service.builder()
                .id(4).serviceType(typeMaterial).name("Thùng carton tiêu chuẩn (60x40x40)")
                .basePrice(new BigDecimal("25000.00")).status(ServiceStatus.ACTIVE).build();

        terrainService = Service.builder()
                .id(5).serviceType(typeTerrain).name("Phụ phí khuân vác thang bộ")
                .basePrice(new BigDecimal("50000.00")).status(ServiceStatus.ACTIVE).build();
    }

    private PricingCalculationRequest createValidRequest() {
        LocalDateTime now = LocalDateTime.now();
        return PricingCalculationRequest.builder()
                .schedule(ScheduleDto.builder()
                        .desiredStartAt(now.plusHours(5))
                        .desiredEndAt(now.plusHours(8))
                        .build())
                .route(RouteDto.builder()
                        .pickup(LocationDto.builder()
                                .address("123 Lê Lợi, Q1, TP.HCM")
                                .lat(new BigDecimal("10.776889"))
                                .lng(new BigDecimal("106.700806"))
                                .propertyType(PropertyType.HOUSE)
                                .floorNumber(0)
                                .hasElevator(true)
                                .walkingDistanceMeters(0)
                                .build())
                        .dropoff(LocationDto.builder()
                                .address("456 Nguyễn Thị Minh Khai, Q3, TP.HCM")
                                .lat(new BigDecimal("10.782622"))
                                .lng(new BigDecimal("106.695844"))
                                .propertyType(PropertyType.APARTMENT)
                                .floorNumber(2)
                                .hasElevator(false)
                                .walkingDistanceMeters(20)
                                .build())
                        .manualDistanceKm(new BigDecimal("15.00"))
                        .build())
                .serviceItems(List.of(
                        ServiceItemDto.builder().serviceId(1).quantity(new BigDecimal("1")).build(),
                        ServiceItemDto.builder().serviceId(2).quantity(new BigDecimal("2")).build(),
                        ServiceItemDto.builder().serviceId(3).quantity(new BigDecimal("1")).build(),
                        ServiceItemDto.builder().serviceId(4).quantity(new BigDecimal("10")).build(),
                        ServiceItemDto.builder().serviceId(5).quantity(new BigDecimal("2")).build()
                ))
                .build();
    }

    @Nested
    @DisplayName("5 Category Breakdown & Calculation Tests")
    class CalculationTests {

        @Test
        @DisplayName("Tính đúng 5 cấu phần chi phí và snapshot đơn giá khi không có voucher")
        void calculatePrice_success_withoutVoucher() {
            PricingCalculationRequest request = createValidRequest();
            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));

            PricingCalculationResponse response = pricingService.calculatePrice(request);

            assertThat(response).isNotNull();
            assertThat(response.getCalculatedDistanceKm()).isEqualByComparingTo("15.00");
            assertThat(response.getIsEstimated()).isTrue();
            assertThat(response.getCurrency()).isEqualTo("VND");

            // Category fees breakdown
            // 1: 450,000 * 1 = 450,000
            // 2: 250,000 * 2 = 500,000
            // 3: 300,000 * 1 = 300,000
            // 4: 25,000 * 10 = 250,000
            // 5: 50,000 * 2 = 100,000
            assertThat(response.getCategoryFees().getBaseTransportFee()).isEqualByComparingTo("450000.00");
            assertThat(response.getCategoryFees().getLaborFee()).isEqualByComparingTo("500000.00");
            assertThat(response.getCategoryFees().getTechnicalFee()).isEqualByComparingTo("300000.00");
            assertThat(response.getCategoryFees().getMaterialFee()).isEqualByComparingTo("250000.00");
            assertThat(response.getCategoryFees().getTerrainSurchargeFee()).isEqualByComparingTo("100000.00");

            // Financial Summary: Subtotal = 1,600,000
            assertThat(response.getFinancialSummary().getSubtotal()).isEqualByComparingTo("1600000.00");
            assertThat(response.getFinancialSummary().getDiscountAmount()).isEqualByComparingTo("0.00");
            assertThat(response.getFinancialSummary().getFinalTotal()).isEqualByComparingTo("1600000.00");

            // Breakdown items snapshot
            assertThat(response.getBreakdownItems()).hasSize(5);
            assertThat(response.getBreakdownItems().get(0).getUnitPrice()).isEqualByComparingTo("450000.00");
            assertThat(response.getBreakdownItems().get(0).getTotalPrice()).isEqualByComparingTo("450000.00");
            assertThat(response.getAppliedVoucher()).isNull();
        }

        @Test
        @DisplayName("Tính cự ly Haversine x 1.3 khi manualDistanceKm là null")
        void calculatePrice_fallbackHaversine_success() {
            PricingCalculationRequest request = createValidRequest();
            request.getRoute().setManualDistanceKm(null);

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));

            PricingCalculationResponse response = pricingService.calculatePrice(request);

            assertThat(response.getCalculatedDistanceKm()).isNotNull();
            assertThat(response.getCalculatedDistanceKm()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Voucher Engine Tests")
    class VoucherTests {

        @Test
        @DisplayName("Áp dụng voucher FIXED_AMOUNT thành công")
        void calculatePrice_withFixedVoucher_success() {
            PricingCalculationRequest request = createValidRequest();
            request.setVoucherCode("GIAM50K");

            Voucher voucher = Voucher.builder()
                    .id(1)
                    .code("GIAM50K")
                    .title("Ưu đãi 50K")
                    .discountType(DiscountType.FIXED_AMOUNT)
                    .discountValue(new BigDecimal("50000.00"))
                    .minOrderValue(new BigDecimal("500000.00"))
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(10))
                    .isActive(true)
                    .build();

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));
            when(voucherRepository.findByCode("GIAM50K")).thenReturn(Optional.of(voucher));

            PricingCalculationResponse response = pricingService.calculatePrice(request);

            assertThat(response.getFinancialSummary().getSubtotal()).isEqualByComparingTo("1600000.00");
            assertThat(response.getFinancialSummary().getDiscountAmount()).isEqualByComparingTo("50000.00");
            assertThat(response.getFinancialSummary().getFinalTotal()).isEqualByComparingTo("1550000.00");

            assertThat(response.getAppliedVoucher()).isNotNull();
            assertThat(response.getAppliedVoucher().getCode()).isEqualTo("GIAM50K");
            assertThat(response.getAppliedVoucher().getDiscountAmount()).isEqualByComparingTo("50000.00");
        }

        @Test
        @DisplayName("Áp dụng voucher PERCENTAGE có trần (maxDiscountAmount)")
        void calculatePrice_withPercentageVoucher_capped() {
            PricingCalculationRequest request = createValidRequest();
            request.setVoucherCode("GIAM10PHANTRAM");

            // 10% của 1,600,000 là 160,000. Nhưng trần tối đa là 100,000.
            Voucher voucher = Voucher.builder()
                    .id(2)
                    .code("GIAM10PHANTRAM")
                    .title("Giảm 10% tối đa 100K")
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new BigDecimal("10.00"))
                    .maxDiscountAmount(new BigDecimal("100000.00"))
                    .minOrderValue(new BigDecimal("200000.00"))
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(10))
                    .isActive(true)
                    .build();

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));
            when(voucherRepository.findByCode("GIAM10PHANTRAM")).thenReturn(Optional.of(voucher));

            PricingCalculationResponse response = pricingService.calculatePrice(request);

            assertThat(response.getFinancialSummary().getSubtotal()).isEqualByComparingTo("1600000.00");
            assertThat(response.getFinancialSummary().getDiscountAmount()).isEqualByComparingTo("100000.00");
            assertThat(response.getFinancialSummary().getFinalTotal()).isEqualByComparingTo("1500000.00");
        }

        @Test
        @DisplayName("Voucher lớn hơn subtotal -> Chặn không cho số âm, finalTotal = 0")
        void calculatePrice_voucherExceedsSubtotal_floorZero() {
            PricingCalculationRequest request = createValidRequest();
            // Chỉ chọn 1 dịch vụ 450,000
            request.setServiceItems(List.of(ServiceItemDto.builder().serviceId(1).quantity(new BigDecimal("1")).build()));
            request.setVoucherCode("SUPERVOUCHER");

            // Voucher giảm 500,000 cho đơn 450,000
            Voucher voucher = Voucher.builder()
                    .id(3)
                    .code("SUPERVOUCHER")
                    .title("Voucher khủng 500K")
                    .discountType(DiscountType.FIXED_AMOUNT)
                    .discountValue(new BigDecimal("500000.00"))
                    .minOrderValue(new BigDecimal("100000.00"))
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(10))
                    .isActive(true)
                    .build();

            when(serviceRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(truckService));
            when(voucherRepository.findByCode("SUPERVOUCHER")).thenReturn(Optional.of(voucher));

            PricingCalculationResponse response = pricingService.calculatePrice(request);

            assertThat(response.getFinancialSummary().getSubtotal()).isEqualByComparingTo("450000.00");
            assertThat(response.getFinancialSummary().getDiscountAmount()).isEqualByComparingTo("450000.00");
            assertThat(response.getFinancialSummary().getFinalTotal()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Edge Cases & Exception Tests")
    class ExceptionTests {

        @Test
        @DisplayName("Lịch trình đặt trước ít hơn 2 tiếng -> ERR_INVALID_SCHEDULE")
        void calculatePrice_leadTimeTooShort_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            // Đặt thời gian bắt đầu chỉ cách hiện tại 30 phút
            request.getSchedule().setDesiredStartAt(LocalDateTime.now().plusMinutes(30));

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_INVALID_SCHEDULE);
                    });
        }

        @Test
        @DisplayName("Thời gian kết thúc diễn ra trước thời gian bắt đầu -> ERR_PRICE_INVALID_INPUT")
        void calculatePrice_endBeforeStart_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            LocalDateTime start = LocalDateTime.now().plusHours(5);
            request.getSchedule().setDesiredStartAt(start);
            request.getSchedule().setDesiredEndAt(start.minusHours(1));

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_PRICE_INVALID_INPUT);
                    });
        }

        @Test
        @DisplayName("Mã dịch vụ không tồn tại trong DB -> ERR_SERVICE_NOT_FOUND")
        void calculatePrice_serviceNotFound_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            // DB chỉ tìm thấy 4 dịch vụ, thiếu 1 dịch vụ
            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService));

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_SERVICE_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("Dịch vụ đang ở trạng thái INACTIVE -> ERR_SERVICE_INACTIVE")
        void calculatePrice_serviceInactive_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            truckService.setStatus(ServiceStatus.INACTIVE);

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_SERVICE_INACTIVE);
                    });
        }

        @Test
        @DisplayName("Mã voucher không tồn tại -> ERR_VOUCHER_NOT_FOUND")
        void calculatePrice_voucherNotFound_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            request.setVoucherCode("KHONGTONTAI");

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));
            when(voucherRepository.findByCode("KHONGTONTAI")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_VOUCHER_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("Voucher đã hết hạn áp dụng -> ERR_VOUCHER_EXPIRED")
        void calculatePrice_voucherExpired_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            request.setVoucherCode("HETHAN");

            Voucher expiredVoucher = Voucher.builder()
                    .id(4)
                    .code("HETHAN")
                    .title("Voucher hết hạn")
                    .discountType(DiscountType.FIXED_AMOUNT)
                    .discountValue(new BigDecimal("50000.00"))
                    .minOrderValue(BigDecimal.ZERO)
                    .startDate(LocalDateTime.now().minusDays(10))
                    .endDate(LocalDateTime.now().minusDays(1)) // Đã hết hạn hôm qua
                    .isActive(true)
                    .build();

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));
            when(voucherRepository.findByCode("HETHAN")).thenReturn(Optional.of(expiredVoucher));

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_VOUCHER_EXPIRED);
                    });
        }

        @Test
        @DisplayName("Đơn hàng chưa đạt giá trị tối thiểu của voucher -> ERR_VOUCHER_MIN_VALUE")
        void calculatePrice_voucherMinValueNotMet_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            request.setVoucherCode("DONTOITHIEU5TRIEU");

            // Đơn hàng hiện tại là 1,600,000, voucher yêu cầu tối thiểu 5,000,000
            Voucher voucher = Voucher.builder()
                    .id(5)
                    .code("DONTOITHIEU5TRIEU")
                    .title("Voucher đơn 5 triệu")
                    .discountType(DiscountType.FIXED_AMOUNT)
                    .discountValue(new BigDecimal("200000.00"))
                    .minOrderValue(new BigDecimal("5000000.00"))
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(10))
                    .isActive(true)
                    .build();

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));
            when(voucherRepository.findByCode("DONTOITHIEU5TRIEU")).thenReturn(Optional.of(voucher));

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_VOUCHER_MIN_VALUE);
                    });
        }

        @Test
        @DisplayName("ServiceType của dịch vụ đang INACTIVE -> ERR_SERVICE_INACTIVE")
        void calculatePrice_serviceTypeInactive_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            // Đặt ServiceType thành inactive, nhưng bản thân Service vẫn ACTIVE
            truckService.getServiceType().setIsActive(false);

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_SERVICE_INACTIVE);
                    });
        }

        @Test
        @DisplayName("Voucher chưa đến ngày bắt đầu hiệu lực -> ERR_VOUCHER_EXPIRED")
        void calculatePrice_voucherNotYetStarted_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            request.setVoucherCode("CHUAMOBAN");

            // startDate là ngày mai -> chưa có hiệu lực
            Voucher voucher = Voucher.builder()
                    .id(6)
                    .code("CHUAMOBAN")
                    .title("Voucher chưa mở bán")
                    .discountType(DiscountType.FIXED_AMOUNT)
                    .discountValue(new BigDecimal("100000.00"))
                    .minOrderValue(BigDecimal.ZERO)
                    .startDate(LocalDateTime.now().plusDays(1))
                    .endDate(LocalDateTime.now().plusDays(30))
                    .isActive(true)
                    .build();

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));
            when(voucherRepository.findByCode("CHUAMOBAN")).thenReturn(Optional.of(voucher));

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_VOUCHER_EXPIRED);
                    });
        }

        @Test
        @DisplayName("Voucher có isActive = false -> ERR_VOUCHER_EXPIRED")
        void calculatePrice_voucherInactive_throwsException() {
            PricingCalculationRequest request = createValidRequest();
            request.setVoucherCode("BITHUY");

            // isActive = false dù còn trong thời hạn
            Voucher voucher = Voucher.builder()
                    .id(7)
                    .code("BITHUY")
                    .title("Voucher đã bị hủy")
                    .discountType(DiscountType.FIXED_AMOUNT)
                    .discountValue(new BigDecimal("100000.00"))
                    .minOrderValue(BigDecimal.ZERO)
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(10))
                    .isActive(false)
                    .build();

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));
            when(voucherRepository.findByCode("BITHUY")).thenReturn(Optional.of(voucher));

            assertThatThrownBy(() -> pricingService.calculatePrice(request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> {
                        AppException appEx = (AppException) ex;
                        assertThat(appEx.getErrorCode()).isEqualTo(ErrorCode.ERR_VOUCHER_EXPIRED);
                    });
        }
    }

    @Nested
    @DisplayName("Voucher Edge Case Tests")
    class VoucherEdgeCaseTests {

        @Test
        @DisplayName("Voucher PERCENTAGE không có maxDiscountAmount -> áp dụng rawDiscount thẳng")
        void calculatePrice_percentageVoucher_withoutMaxCap_appliesRawDiscount() {
            PricingCalculationRequest request = createValidRequest();
            request.setVoucherCode("GIAM5PHANTRAM");

            // subtotal = 1,600,000 → 5% = 80,000 (không có trần nên áp thẳng)
            Voucher voucher = Voucher.builder()
                    .id(8)
                    .code("GIAM5PHANTRAM")
                    .title("Giảm 5% không giới hạn")
                    .discountType(DiscountType.PERCENTAGE)
                    .discountValue(new BigDecimal("5.00"))
                    .maxDiscountAmount(null)
                    .minOrderValue(BigDecimal.ZERO)
                    .startDate(LocalDateTime.now().minusDays(1))
                    .endDate(LocalDateTime.now().plusDays(10))
                    .isActive(true)
                    .build();

            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));
            when(voucherRepository.findByCode("GIAM5PHANTRAM")).thenReturn(Optional.of(voucher));

            PricingCalculationResponse response = pricingService.calculatePrice(request);

            // rawDiscount = 1,600,000 * 5% = 80,000 (áp thẳng vì không có trần)
            assertThat(response.getFinancialSummary().getSubtotal()).isEqualByComparingTo("1600000.00");
            assertThat(response.getFinancialSummary().getDiscountAmount()).isEqualByComparingTo("80000.00");
            assertThat(response.getFinancialSummary().getFinalTotal()).isEqualByComparingTo("1520000.00");
        }
    }

    @Nested
    @DisplayName("Idempotency Tests")
    class IdempotencyTests {

        @Test
        @DisplayName("Gọi calculatePrice 10 lần với cùng input -> kết quả giống hệt nhau (tính ổn định)")
        void calculatePrice_calledTenTimes_producesIdenticalResults() {
            when(serviceRepository.findAllByIdIn(anyCollection()))
                    .thenReturn(List.of(truckService, laborService, technicalService, materialService, terrainService));

            PricingCalculationResponse first = pricingService.calculatePrice(createValidRequest());

            for (int i = 1; i < 10; i++) {
                PricingCalculationResponse result = pricingService.calculatePrice(createValidRequest());

                assertThat(result.getFinancialSummary().getSubtotal())
                        .as("Lần thứ %d: subtotal phải giống lần đầu", i + 1)
                        .isEqualByComparingTo(first.getFinancialSummary().getSubtotal());

                assertThat(result.getFinancialSummary().getDiscountAmount())
                        .as("Lần thứ %d: discountAmount phải giống lần đầu", i + 1)
                        .isEqualByComparingTo(first.getFinancialSummary().getDiscountAmount());

                assertThat(result.getFinancialSummary().getFinalTotal())
                        .as("Lần thứ %d: finalTotal phải giống lần đầu", i + 1)
                        .isEqualByComparingTo(first.getFinancialSummary().getFinalTotal());

                assertThat(result.getCalculatedDistanceKm())
                        .as("Lần thứ %d: calculatedDistanceKm phải giống lần đầu", i + 1)
                        .isEqualByComparingTo(first.getCalculatedDistanceKm());
            }
        }
    }
}
