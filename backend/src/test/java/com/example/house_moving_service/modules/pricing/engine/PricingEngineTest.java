package com.example.house_moving_service.modules.pricing.engine;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.entity.ServiceType;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.pricing.dto.PricingInput;
import com.example.house_moving_service.modules.pricing.dto.PricingInput.AccessInfo;
import com.example.house_moving_service.modules.pricing.dto.PricingInput.ServiceLine;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.voucher.enums.DiscountType;
import com.example.house_moving_service.modules.voucher.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingEngineTest {

    private static final AccessInfo GROUND = new AccessInfo(0, false, 0);

    @Mock
    private ServiceLookupService serviceLookupService;

    @Mock
    private VoucherService voucherService;

    @InjectMocks
    private PricingEngine pricingEngine;

    private Service truck;
    private Service labor;
    private Service technical;
    private Service material;
    private Service terrain;

    @BeforeEach
    void setUp() {
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
        return new PricingInput(new BigDecimal("15.00"), GROUND, GROUND, List.of(lines), voucherCode);
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

        assertThat(res.getCalculatedDistanceKm()).isEqualByComparingTo("15.00");
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
                new BigDecimal("15.00"), GROUND, GROUND, fullInput().services(), "GIAM10"));

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
        when(serviceLookupService.findAllByIds(anyCollection())).thenReturn(List.of(truck));

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
}
