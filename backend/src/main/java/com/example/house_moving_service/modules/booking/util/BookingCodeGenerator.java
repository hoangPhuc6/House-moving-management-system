package com.example.house_moving_service.modules.booking.util;

import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCodeGenerator {

    private final BookingRepository bookingRepository;

    private static final String CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // Bỏ 0, 1, I, O để tránh nhầm lẫn
    private static final int RANDOM_LENGTH = 4;
    private static final int MAX_RETRIES = 5;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Sinh mã đơn hàng ngẫu nhiên với định dạng: BKG + yyMMdd + 4 ký tự ngẫu nhiên
     * Ví dụ: BKG261018A7K2
     */
    public static String generateRawCode() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        StringBuilder sb = new StringBuilder("BKG").append(datePart);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Sinh mã đơn hàng duy nhất, tự động kiểm tra đối soát với cơ sở dữ liệu và thử lại nếu va chạm
     */
    public String generateUniqueBookingCode() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String candidateCode = generateRawCode();
            if (!bookingRepository.existsByBookingCode(candidateCode)) {
                return candidateCode;
            }
            log.warn("Collision detected for booking code: {}. Retrying attempt {}/{}", candidateCode, attempt, MAX_RETRIES);
        }
        // Fallback khẩn cấp nếu sau 5 lần vẫn trùng (kết hợp nanotime)
        String fallbackCode = "BKG" + LocalDate.now().format(DATE_FORMATTER) + Long.toHexString(System.nanoTime()).toUpperCase();
        if (fallbackCode.length() > 30) {
            fallbackCode = fallbackCode.substring(0, 30);
        }
        return fallbackCode;
    }
}

