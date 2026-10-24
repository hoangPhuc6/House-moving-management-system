package com.example.house_moving_service.modules.booking.util;

import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCodeGeneratorTest {

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingCodeGenerator bookingCodeGenerator;

    @Test
    @DisplayName("Should generate code with prefix BKG and valid length")
    void testGenerateRawCode_Format() {
        String code = BookingCodeGenerator.generateRawCode();

        assertThat(code).isNotNull();
        assertThat(code).startsWith("BKG");
        // BKG (3) + yyMMdd (6) + random (4) = 13 chars
        assertThat(code).hasSize(13);
    }

    @Test
    @DisplayName("Should return code on first attempt when no collision")
    void testGenerateUniqueBookingCode_FirstAttemptSuccess() {
        when(bookingRepository.existsByBookingCode(anyString())).thenReturn(false);

        String uniqueCode = bookingCodeGenerator.generateUniqueBookingCode();

        assertThat(uniqueCode).isNotNull();
        assertThat(uniqueCode).startsWith("BKG");
        verify(bookingRepository, times(1)).existsByBookingCode(anyString());
    }

    @Test
    @DisplayName("Should retry when collision occurs and succeed on subsequent attempt")
    void testGenerateUniqueBookingCode_RetryOnCollision() {
        when(bookingRepository.existsByBookingCode(anyString()))
                .thenReturn(true)  // 1st collision
                .thenReturn(false); // 2nd success

        String uniqueCode = bookingCodeGenerator.generateUniqueBookingCode();

        assertThat(uniqueCode).isNotNull();
        assertThat(uniqueCode).startsWith("BKG");
        verify(bookingRepository, times(2)).existsByBookingCode(anyString());
    }
}

