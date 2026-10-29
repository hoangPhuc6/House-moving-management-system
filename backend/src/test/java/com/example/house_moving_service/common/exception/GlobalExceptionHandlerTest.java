package com.example.house_moving_service.common.exception;

import com.example.house_moving_service.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/management/surveys");

    private DataIntegrityViolationException violation(String dbMessage) {
        return new DataIntegrityViolationException("could not execute statement", new SQLException(dbMessage));
    }

    @Test
    @DisplayName("Vi phạm uq_booking_surveys_staff_slot → ERR_SURVEY_STAFF_CONFLICT (409)")
    void testStaffSlotViolation() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(violation(
                "ERROR: duplicate key value violates unique constraint \"uq_booking_surveys_staff_slot\""), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.ERR_SURVEY_STAFF_CONFLICT.getCode());
    }

    @Test
    @DisplayName("Vi phạm uq_booking_surveys_one_active_per_booking → ERR_SURVEY_ACTIVE_EXISTS (409)")
    void testOneActivePerBookingViolation() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(violation(
                "ERROR: duplicate key value violates unique constraint \"uq_booking_surveys_one_active_per_booking\""), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.ERR_SURVEY_ACTIVE_EXISTS.getCode());
    }

    @Test
    @DisplayName("Vi phạm ràng buộc khác vẫn trả lỗi hệ thống như trước (500)")
    void testOtherViolation_FallsBackToInternalError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(violation(
                "ERROR: duplicate key value violates unique constraint \"users_email_key\""), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.ERR_INTERNAL_SERVER.getCode());
    }
}
