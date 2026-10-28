package com.example.house_moving_service.modules.pricing.engine;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.entity.ServiceType;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import com.example.house_moving_service.modules.catalog.enums.SystemServiceCode;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.pricing.config.PricingProperties;
import com.example.house_moving_service.modules.pricing.dto.PricingInput;
import com.example.house_moving_service.modules.pricing.dto.PricingInput.AccessInfo;
import com.example.house_moving_service.modules.pricing.dto.PricingInput.ServiceLine;
import com.example.house_moving_service.modules.pricing.dto.response.BreakdownItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.voucher.enums.DiscountType;
import com.example.house_moving_service.modules.voucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingEngineTest {

    private static final AccessInfo GROUND = new AccessInfo(0, false, 0);

    @Mock
    private ServiceLookupService serviceLookupService;

    @Mock
    private VoucherService voucherService;

    private PricingEngine pricingEngine;

    private Service truck;
    private Service labor;
    private Service technical;
    private Service material;
    private Service terrain;

    @BeforeEach
    void setUp() {
        PricingProperties pricingProperties = new PricingProperties();
        pricingProperties.setBaseTransportIncludedKm(new BigDecimal("10.0"));
        pricingProperties.setDeepAlleyMeters(50);
        pricingEngine = new PricingEngine(serviceLookupService, voucherService, pricingProperties);

        truck = service(1, "Xe tải 1.5 Tấn", "450000.00", type(1, "Xe tải vận chuyển", FeeCategory.TRANSPORT));
        labor = service(2, "Nhân công bốc xếp", "250000.00", type(2, "Bốc xếp", FeeCategory.LABOR));
        technical = service(3, "Tháo lắp máy lạnh", "300000.00", type(3, "Kỹ thuật", FeeCategory.TECHNICAL));
        material = service(4, "Thùng carton", "25000.00", type(4, "Vật tư", FeeCategory.MATERIAL));
        terrain = service(5, "Phụ phí thang bộ", "50000.00", type(5, "Địa hình", FeeCategory.TERRAIN));
    }

    private static ServiceType type(int id, String name, FeeCategory category) {
        return ServiceType.builder().id(id).name(name).isActive(true).feeCategory(category).build();
    }

    private static Service service(int id, String name, String price, ServiceType type) {
        return Service.builder().id(id).name(name).basePrice(new BigDecimal(price))
                .status(ServiceStatus.ACTIVE).serviceType(type).build();
    }

    private static PricingInput input(String voucherCode, ServiceLine... lines) {
        return new PricingInput(new BigDecimal("8.00"), GROUND, GROUND, List.of(lines), voucherCode);
    }

    private static PricingInput fullInput() {
        return input(null,
                new ServiceLine(1, 1), new ServiceLine(2, 2), new ServiceLine(3, 1),
                new ServiceLine(4, 10), new ServiceLine(5, 2));
    }

    private void stubAllServices() {
        when(serviceLookupService.findAllByIds(anyCollection()))
                .thenReturn(List.of(truck, labor, technical, material, terrain));
    }

    @Test
    @DisplayName("Cộng đúng 5 nhóm phí, subtotal và breakdown (quantity nguyên)")
    void calculate_fiveCategories() {
        stubAllServices();

        PricingCalculationResponse res = pricingEngine.calculate(fullInput());

        assertThat(res.getCalculatedDistanceKm()).isEqualByComparingTo("8.00");
        assertThat(res.getChargeableExtraKm()).isZero();
        assertThat(res.getBreakdownItems()).noneMatch(BreakdownItemDto::isAutoApplied);
        assertThat(res.getCategoryFees().getBaseTransportFee()).isEqualByComparingTo("450000.00");
        assertThat(res.getCategoryFees().getLaborFee()).isEqualByComparingTo("500000.00");
        assertThat(res.getCategoryFees().getTechnicalFee()).isEqualByComparingTo("300000.00");
        assertThat(res.getCategoryFees().getMaterialFee()).isEqualByComparingTo("250000.00");
        assertThat(res.getCategoryFees().getTerrainSurchargeFee()).isEqualByComparingTo("100000.00");
        assertThat(res.getFinancialSummary().getSubtotal()).isEqualByComparingTo("1600000.00");
        assertThat(res.getFinancialSummary().getFinalTotal()).isEqualByComparingTo("1600000.00");
        assertThat(res.getBreakdownItems()).hasSize(5);
        assertThat(res.getBreakdownItems().get(3).getQuantity()).isEqualTo(10);
        assertThat(res.getBreakdownItems().get(3).getTotalPrice()).isEqualByComparingTo("250000.00");
        assertThat(res.getAppliedVoucher()).isNull();
        verifyNoInteractions(voucherService);
    }

    @Test
    @DisplayName("Nhóm phí lấy theo fee_category: tên loại dịch vụ gây nhầm vẫn cộng đúng nhóm")
    void calculate_categoryFromFeeCategoryNotName() {
        // Tên chứa "xe" / "phụ phí" — cách dò tên cũ sẽ xếp sai
        Service misleading = service(9, "Phụ phí xe nâng", "200000.00", type(9, "Xe phụ trợ kỹ thuật", FeeCategory.TECHNICAL));
        when(serviceLookupService.findAllByIds(anyCollection())).thenReturn(List.of(misleading));

        PricingCalculationResponse res = pricingEngine.calculate(input(null, new ServiceLine(9, 1)));

        assertThat(res.getCategoryFees().getTechnicalFee()).isEqualByComparingTo("200000.00");
        assertThat(res.getCategoryFees().getBaseTransportFee()).isEqualByComparingTo("0.00");
        assertThat(res.getCategoryFees().getTerrainSurchargeFee()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Đổi tên loại dịch vụ không làm đổi nhóm phí")
    void calculate_renameTypeKeepsCategory() {
        stubAllServices();
        PricingCalculationResponse before = pricingEngine.calculate(fullInput());

        labor.getServiceType().setName("Tên mới hoàn toàn không liên quan");
        PricingCalculationResponse after = pricingEngine.calculate(fullInput());

        assertThat(after.getCategoryFees().getLaborFee()).isEqualByComparingTo(before.getCategoryFees().getLaborFee());
        assertThat(after.getFinancialSummary().getSubtotal()).isEqualByComparingTo(before.getFinancialSummary().getSubtotal());
    }

    @Test
    @DisplayName("Cùng đầu vào gọi 2 lần → kết quả bằng nhau")
    void calculate_deterministic() {
        stubAllServices();

        PricingCalculationResponse first = pricingEngine.calculate(fullInput());
        PricingCalculationResponse second = pricingEngine.calculate(fullInput());

        assertThat(second).usingRecursiveComparison().isEqualTo(first);
    }

    @Test
    @DisplayName("Voucher PERCENTAGE có trần: giảm tối đa maxDiscountAmount")
    void calculate_percentageVoucherCapped() {
        stubAllServices();
        Voucher voucher = Voucher.builder().id(2).code("GIAM10").title("Giảm 10%")
                .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10.00"))
                .maxDiscountAmount(new BigDecimal("100000.00")).minOrderValue(BigDecimal.ZERO).build();
        when(voucherService.getValidVoucher("GIAM10")).thenReturn(voucher);

        PricingCalculationResponse res = pricingEngine.calculate(new PricingInput(
                new BigDecimal("8.00"), GROUND, GROUND, fullInput().services(), "GIAM10"));

        assertThat(res.getFinancialSummary().getDiscountAmount()).isEqualByComparingTo("100000.00");
        assertThat(res.getFinancialSummary().getFinalTotal()).isEqualByComparingTo("1500000.00");
        assertThat(res.getAppliedVoucher().getCode()).isEqualTo("GIAM10");
    }

    @Test
    @DisplayName("Voucher lớn hơn subtotal → finalTotal = 0")
    void calculate_voucherExceedsSubtotal() {
        when(serviceLookupService.findAllByIds(anyCollection())).thenReturn(List.of(truck));
        Voucher voucher = Voucher.builder().id(3).code("BIG").title("500K")
                .discountType(DiscountType.FIXED_AMOUNT).discountValue(new BigDecimal("500000.00")).build();
        when(voucherService.getValidVoucher("BIG")).thenReturn(voucher);

        PricingCalculationResponse res = pricingEngine.calculate(input("BIG", new ServiceLine(1, 1)));

        assertThat(res.getFinancialSummary().getDiscountAmount()).isEqualByComparingTo("450000.00");
        assertThat(res.getFinancialSummary().getFinalTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Voucher chưa đạt giá trị tối thiểu → ERR_VOUCHER_MIN_VALUE")
    void calculate_voucherMinValueNotMet() {
        when(serviceLookupService.findAllByIds(anyCollection())).thenReturn(List.of(truck));
        Voucher voucher = Voucher.builder().id(5).code("MIN5M").title("Đơn 5 triệu")
                .discountType(DiscountType.FIXED_AMOUNT).discountValue(new BigDecimal("200000.00"))
                .minOrderValue(new BigDecimal("5000000.00")).build();
        when(voucherService.getValidVoucher("MIN5M")).thenReturn(voucher);

        assertThatThrownBy(() -> pricingEngine.calculate(input("MIN5M", new ServiceLine(1, 1))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERR_VOUCHER_MIN_VALUE);
    }

    @Test
    @DisplayName("Danh sách dịch vụ rỗng → ERR_PRICE_INVALID_INPUT")
    void calculate_emptyServices() {
        assertThatThrownBy(() -> pricingEngine.calculate(input(null)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERR_PRICE_INVALID_INPUT);
    }

    @Test
    @DisplayName("Số lượng < 1 (bên gọi bỏ qua validation) → ERR_PRICE_INVALID_INPUT")
    void calculate_quantityBelowOne() {
        assertThatThrownBy(() -> pricingEngine.calculate(input(null, new ServiceLine(1, 0))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERR_PRICE_INVALID_INPUT);
    }

    @Test
    @DisplayName("Dịch vụ không tồn tại → ERR_SERVICE_NOT_FOUND")
    void calculate_serviceNotFound() {
        when(serviceLookupService.findAllByIds(anyCollection())).thenReturn(List.of(truck));

        assertThatThrownBy(() -> pricingEngine.calculate(input(null, new ServiceLine(1, 1), new ServiceLine(99, 1))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERR_SERVICE_NOT_FOUND);
    }

    @Test
    @DisplayName("Loại dịch vụ đã tắt → ERR_SERVICE_INACTIVE")
    void calculate_serviceTypeInactive() {
        truck.getServiceType().setIsActive(false);
        when(serviceLookupService.findAllByIds(anyCollection())).thenReturn(List.of(truck));

        assertThatThrownBy(() -> pricingEngine.calculate(input(null, new ServiceLine(1, 1))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ERR_SERVICE_INACTIVE);
    }

    @Nested
    @DisplayName("Phụ phí tự động (vượt km, thang bộ, hẻm sâu)")
    class AutoSurchargeTests {

        private Service extraKm;
        private Service stairs;
        private Service deepAlley;

        @BeforeEach
        void setUpSystemServices() {
            extraKm = systemService(30, "Phụ phí vượt km (mỗi km)", "15000.00", truck.getServiceType(), SystemServiceCode.SURCHARGE_EXTRA_KM);
            stairs = systemService(31, "Phụ phí khuân vác thang bộ (mỗi lầu)", "50000.00", terrain.getServiceType(), SystemServiceCode.SURCHARGE_STAIRS);
            deepAlley = systemService(32, "Phụ phí hẻm sâu (> 50 mét)", "100000.00", terrain.getServiceType(), SystemServiceCode.SURCHARGE_DEEP_ALLEY);
            lenient().when(serviceLookupService.findByCode(SystemServiceCode.SURCHARGE_EXTRA_KM)).thenReturn(Optional.of(extraKm));
            lenient().when(serviceLookupService.findByCode(SystemServiceCode.SURCHARGE_STAIRS)).thenReturn(Optional.of(stairs));
            lenient().when(serviceLookupService.findByCode(SystemServiceCode.SURCHARGE_DEEP_ALLEY)).thenReturn(Optional.of(deepAlley));
            lenient().when(serviceLookupService.findAllByIds(anyCollection())).thenReturn(List.of(truck));
        }

        private Service systemService(int id, String name, String price, ServiceType type, String code) {
            return Service.builder().id(id).name(name).basePrice(new BigDecimal(price))
                    .status(ServiceStatus.ACTIVE).serviceType(type).code(code).build();
        }

        // Khách chỉ chọn xe tải (450.000đ, TRANSPORT)
        private PricingCalculationResponse calc(String distanceKm, AccessInfo pickup, AccessInfo dropoff) {
            return pricingEngine.calculate(new PricingInput(new BigDecimal(distanceKm), pickup, dropoff,
                    List.of(new ServiceLine(1, 1)), null));
        }

        private List<BreakdownItemDto> autoLines(PricingCalculationResponse res) {
            return res.getBreakdownItems().stream().filter(BreakdownItemDto::isAutoApplied).toList();
        }

        @ParameterizedTest(name = "{0} km → vượt {1} km")
        @CsvSource({"8.00, 0", "10.00, 0", "10.20, 1", "15.00, 5"})
        @DisplayName("Vượt km: làm tròn lên phần vượt quá 10 km, cộng vào TRANSPORT")
        void extraKm(String distanceKm, int expectedExtraKm) {
            PricingCalculationResponse res = calc(distanceKm, GROUND, GROUND);

            assertThat(res.getIncludedKm()).isEqualByComparingTo("10.0");
            assertThat(res.getChargeableExtraKm()).isEqualTo(expectedExtraKm);
            BigDecimal expectedTransport = new BigDecimal("450000.00").add(new BigDecimal("15000.00").multiply(BigDecimal.valueOf(expectedExtraKm)));
            assertThat(res.getCategoryFees().getBaseTransportFee()).isEqualByComparingTo(expectedTransport);
            assertThat(autoLines(res)).hasSize(expectedExtraKm > 0 ? 1 : 0);
        }

        @Test
        @DisplayName("Vượt km: dòng phụ phí có note giải thích")
        void extraKm_note() {
            BreakdownItemDto line = autoLines(calc("15.00", GROUND, GROUND)).getFirst();

            assertThat(line.getServiceId()).isEqualTo(30);
            assertThat(line.getQuantity()).isEqualTo(5);
            assertThat(line.getTotalPrice()).isEqualByComparingTo("75000.00");
            assertThat(line.getNote()).isEqualTo("Vượt 5 km (15 km, đã gồm 10 km)");
        }

        @Test
        @DisplayName("Thang bộ: tầng 1 không thang máy → không phụ phí")
        void stairs_firstFloor_none() {
            assertThat(autoLines(calc("8.00", new AccessInfo(1, false, 0), GROUND))).isEmpty();
        }

        @Test
        @DisplayName("Thang bộ: tầng 5 không thang máy → 4 × 50.000đ vào TERRAIN")
        void stairs_fifthFloorNoElevator() {
            PricingCalculationResponse res = calc("8.00", new AccessInfo(5, false, 0), GROUND);

            List<BreakdownItemDto> lines = autoLines(res);
            assertThat(lines).hasSize(1);
            assertThat(lines.getFirst().getQuantity()).isEqualTo(4);
            assertThat(lines.getFirst().getNote()).isEqualTo("Điểm lấy hàng: tầng 5, không thang máy (4 tầng)");
            assertThat(res.getCategoryFees().getTerrainSurchargeFee()).isEqualByComparingTo("200000.00");
            assertThat(res.getFinancialSummary().getSubtotal()).isEqualByComparingTo("650000.00");
        }

        @Test
        @DisplayName("Thang bộ: tầng 5 có thang máy → không phụ phí")
        void stairs_fifthFloorWithElevator_none() {
            assertThat(autoLines(calc("8.00", new AccessInfo(5, true, 0), GROUND))).isEmpty();
        }

        @Test
        @DisplayName("Thang bộ: cả hai điểm tầng 3 không thang máy → 2 dòng, mỗi dòng 2 tầng; tra dịch vụ 1 lần")
        void stairs_bothPoints() {
            AccessInfo thirdFloor = new AccessInfo(3, false, 0);
            PricingCalculationResponse res = calc("8.00", thirdFloor, thirdFloor);

            List<BreakdownItemDto> lines = autoLines(res);
            assertThat(lines).hasSize(2);
            assertThat(lines).allMatch(l -> l.getQuantity() == 2);
            assertThat(lines.get(0).getNote()).startsWith("Điểm lấy hàng");
            assertThat(lines.get(1).getNote()).startsWith("Điểm giao hàng");
            assertThat(res.getCategoryFees().getTerrainSurchargeFee()).isEqualByComparingTo("200000.00");
            verify(serviceLookupService, times(1)).findByCode(SystemServiceCode.SURCHARGE_STAIRS);
        }

        @ParameterizedTest(name = "đi bộ {0} m → {1} dòng hẻm sâu")
        @CsvSource({"50, 0", "51, 1"})
        @DisplayName("Hẻm sâu: chỉ tính khi cự ly đi bộ > 50 m")
        void deepAlley(int walkingMeters, int expectedLines) {
            PricingCalculationResponse res = calc("8.00", GROUND, new AccessInfo(0, false, walkingMeters));

            List<BreakdownItemDto> lines = autoLines(res);
            assertThat(lines).hasSize(expectedLines);
            if (expectedLines > 0) {
                assertThat(lines.getFirst().getQuantity()).isEqualTo(1);
                assertThat(lines.getFirst().getNote()).isEqualTo("Điểm giao hàng: đi bộ 51 m từ chỗ đậu xe");
                assertThat(res.getCategoryFees().getTerrainSurchargeFee()).isEqualByComparingTo("100000.00");
            }
        }

        @Test
        @DisplayName("Khách tự chọn dịch vụ thang bộ × 3 → dòng đó bị bỏ, chỉ còn dòng tự tính")
        void customerPickedSystemService_ignored() {
            when(serviceLookupService.findAllByIds(anyCollection())).thenReturn(List.of(truck, stairs));

            PricingCalculationResponse res = pricingEngine.calculate(new PricingInput(new BigDecimal("8.00"),
                    GROUND, new AccessInfo(3, false, 0),
                    List.of(new ServiceLine(1, 1), new ServiceLine(31, 3)), null));

            assertThat(res.getBreakdownItems()).hasSize(2);
            assertThat(res.getBreakdownItems()).filteredOn(l -> l.getServiceId() == 31)
                    .singleElement()
                    .satisfies(l -> {
                        assertThat(l.isAutoApplied()).isTrue();
                        assertThat(l.getQuantity()).isEqualTo(2);
                    });
            assertThat(res.getFinancialSummary().getSubtotal()).isEqualByComparingTo("550000.00");
        }

        @Test
        @DisplayName("Dịch vụ phụ phí bị tắt → không cộng, không lỗi")
        void systemServiceInactive_skipped() {
            stairs.setStatus(ServiceStatus.INACTIVE);

            PricingCalculationResponse res = calc("15.00", new AccessInfo(5, false, 0), GROUND);

            assertThat(autoLines(res)).extracting(BreakdownItemDto::getServiceId).containsExactly(30);
            assertThat(res.getCategoryFees().getTerrainSurchargeFee()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Dịch vụ phụ phí không có trong DB → không cộng, không lỗi")
        void systemServiceMissing_skipped() {
            when(serviceLookupService.findByCode(SystemServiceCode.SURCHARGE_EXTRA_KM)).thenReturn(Optional.empty());

            PricingCalculationResponse res = calc("15.00", GROUND, GROUND);

            assertThat(autoLines(res)).isEmpty();
            assertThat(res.getChargeableExtraKm()).isEqualTo(5);
            assertThat(res.getFinancialSummary().getSubtotal()).isEqualByComparingTo("450000.00");
        }

        @Test
        @DisplayName("Voucher % tính trên tạm tính đã gồm phụ phí")
        void voucherOnSubtotalWithSurcharges() {
            Voucher voucher = Voucher.builder().id(9).code("GIAM10").title("Giảm 10%")
                    .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("10.00"))
                    .minOrderValue(BigDecimal.ZERO).build();
            when(voucherService.getValidVoucher("GIAM10")).thenReturn(voucher);

            // 450.000 + 5 km × 15.000 + 4 tầng × 50.000 + hẻm 100.000 = 825.000 → giảm 82.500
            PricingCalculationResponse res = pricingEngine.calculate(new PricingInput(new BigDecimal("15.00"),
                    new AccessInfo(5, false, 0), new AccessInfo(0, false, 80),
                    List.of(new ServiceLine(1, 1)), "GIAM10"));

            assertThat(res.getFinancialSummary().getSubtotal()).isEqualByComparingTo("825000.00");
            assertThat(res.getFinancialSummary().getDiscountAmount()).isEqualByComparingTo("82500.00");
            assertThat(res.getFinancialSummary().getFinalTotal()).isEqualByComparingTo("742500.00");
        }
    }
}
