package com.example.house_moving_service.dto;

import com.example.house_moving_service.dto.request.CreateServiceRequest;
import com.example.house_moving_service.dto.request.CreateServiceTypeRequest;
import com.example.house_moving_service.dto.request.UpdateServiceRequest;
import com.example.house_moving_service.dto.request.UpdateServiceStatusRequest;
import com.example.house_moving_service.dto.request.UpdateServiceTypeRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("CreateServiceTypeRequest: Hợp lệ khi đủ trường chuẩn")
    void testCreateServiceTypeRequest_valid() {
        CreateServiceTypeRequest req = CreateServiceTypeRequest.builder()
                .name("Cước xe tải")
                .description("Vận chuyển xe tải")
                .isActive(true)
                .build();

        Set<ConstraintViolation<CreateServiceTypeRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("CreateServiceTypeRequest: Bắt lỗi khi name rỗng hoặc vượt quá 255 ký tự")
    void testCreateServiceTypeRequest_invalidName() {
        // Name rỗng
        CreateServiceTypeRequest blankReq = CreateServiceTypeRequest.builder()
                .name("   ")
                .build();
        Set<ConstraintViolation<CreateServiceTypeRequest>> violations = validator.validate(blankReq);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));

        // Name vượt quá 255 ký tự
        CreateServiceTypeRequest longNameReq = CreateServiceTypeRequest.builder()
                .name("A".repeat(256))
                .build();
        violations = validator.validate(longNameReq);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("tối đa 255 ký tự")
                || v.getMessage().contains("không được vượt quá 255 ký tự")));
    }

    @Test
    @DisplayName("UpdateServiceTypeRequest: Bắt lỗi khi isActive null hoặc name rỗng")
    void testUpdateServiceTypeRequest_invalid() {
        UpdateServiceTypeRequest req = UpdateServiceTypeRequest.builder()
                .name("")
                .isActive(null)
                .build();

        Set<ConstraintViolation<UpdateServiceTypeRequest>> violations = validator.validate(req);
        assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("CreateServiceRequest: Bắt lỗi khi thiếu serviceTypeId, name hoặc basePrice")
    void testCreateServiceRequest_missingRequiredFields() {
        CreateServiceRequest req = CreateServiceRequest.builder().build();

        Set<ConstraintViolation<CreateServiceRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());

        Set<String> violatedFields = violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(violatedFields.contains("serviceTypeId"));
        assertTrue(violatedFields.contains("name"));
        assertTrue(violatedFields.contains("basePrice"));
    }

    @Test
    @DisplayName("CreateServiceRequest: Bắt lỗi giá tiền bằng 0, số âm hoặc sai chữ số thập phân")
    void testCreateServiceRequest_invalidPrice() {
        // Giá bằng 0
        CreateServiceRequest zeroPriceReq = CreateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Dịch vụ test")
                .basePrice(BigDecimal.ZERO)
                .build();
        Set<ConstraintViolation<CreateServiceRequest>> violations = validator.validate(zeroPriceReq);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("basePrice")));

        // Giá âm
        CreateServiceRequest negativePriceReq = CreateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Dịch vụ test")
                .basePrice(new BigDecimal("-50000.00"))
                .build();
        violations = validator.validate(negativePriceReq);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("basePrice")));

        // Quá 2 chữ số thập phân
        CreateServiceRequest fractionReq = CreateServiceRequest.builder()
                .serviceTypeId(1)
                .name("Dịch vụ test")
                .basePrice(new BigDecimal("10000.123"))
                .build();
        violations = validator.validate(fractionReq);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("basePrice")));
    }

    @Test
    @DisplayName("UpdateServiceRequest: Hợp lệ với dữ liệu chuẩn")
    void testUpdateServiceRequest_valid() {
        UpdateServiceRequest req = UpdateServiceRequest.builder()
                .serviceTypeId(2)
                .name("Bốc xếp tầng lầu")
                .description("Có hỗ trợ thang bộ")
                .basePrice(new BigDecimal("250000.00"))
                .build();

        Set<ConstraintViolation<UpdateServiceRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("UpdateServiceStatusRequest: Hợp lệ với 'ACTIVE' và 'INACTIVE'")
    void testUpdateServiceStatusRequest_valid() {
        UpdateServiceStatusRequest activeReq = UpdateServiceStatusRequest.builder()
                .status("ACTIVE")
                .build();
        assertTrue(validator.validate(activeReq).isEmpty());
        assertEquals("ACTIVE", activeReq.toServiceStatus().name());

        UpdateServiceStatusRequest inactiveReq = UpdateServiceStatusRequest.builder()
                .status("INACTIVE")
                .build();
        assertTrue(validator.validate(inactiveReq).isEmpty());
        assertEquals("INACTIVE", inactiveReq.toServiceStatus().name());
    }

    @Test
    @DisplayName("UpdateServiceStatusRequest: Bắt lỗi khi truyền trạng thái không thuộc ACTIVE / INACTIVE")
    void testUpdateServiceStatusRequest_invalidStatus() {
        // Giá trị lạ
        UpdateServiceStatusRequest invalidReq = UpdateServiceStatusRequest.builder()
                .status("PENDING")
                .build();
        Set<ConstraintViolation<UpdateServiceStatusRequest>> violations = validator.validate(invalidReq);
        assertFalse(violations.isEmpty());
        assertEquals("Trạng thái không hợp lệ. Chỉ chấp nhận 'ACTIVE' hoặc 'INACTIVE'",
                violations.iterator().next().getMessage());

        // Giá trị chữ thường (không đúng chuẩn)
        UpdateServiceStatusRequest lowercaseReq = UpdateServiceStatusRequest.builder()
                .status("active")
                .build();
        violations = validator.validate(lowercaseReq);
        assertFalse(violations.isEmpty());

        // Giá trị rỗng
        UpdateServiceStatusRequest blankReq = UpdateServiceStatusRequest.builder()
                .status("")
                .build();
        violations = validator.validate(blankReq);
        assertFalse(violations.isEmpty());
    }
}

