package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.survey.enums.AttachmentType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Quy tắc file đính kèm khảo sát: loại file suy ra từ MIME (không nhận từ client), đuôi file phải khớp MIME,
 * dung lượng theo loại, và các byte đầu file phải đúng định dạng (chặn đổi đuôi, ví dụ .exe đổi thành .jpg).
 */
@Component
class SurveyAttachmentPolicy {

    /** Số byte đầu file cần đọc để kiểm tra định dạng. */
    static final int HEADER_LENGTH = 16;

    private static final long MB = 1024L * 1024;
    private static final Map<AttachmentType, Long> MAX_SIZE = Map.of(
            AttachmentType.IMAGE, 10 * MB,
            AttachmentType.VIDEO, 50 * MB,
            AttachmentType.DOCUMENT, 10 * MB);

    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "heim", "heis", "hevc", "mif1", "msf1");
    private static final Set<String> MOV_ATOMS = Set.of("moov", "mdat", "wide", "free", "skip");

    /** Một định dạng được phép: MIME chuẩn, loại, các đuôi chấp nhận (đuôi đầu tiên dùng khi sinh key), cách nhận diện byte đầu. */
    record Format(String contentType, AttachmentType type, List<String> extensions, Predicate<byte[]> signature) {
        String defaultExtension() {
            return extensions.get(0);
        }
    }

    private static final List<Format> FORMATS = List.of(
            new Format("image/jpeg", AttachmentType.IMAGE, List.of("jpg", "jpeg"),
                    h -> startsWith(h, 0, 0xFF, 0xD8, 0xFF)),
            new Format("image/png", AttachmentType.IMAGE, List.of("png"),
                    h -> startsWith(h, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
            new Format("image/webp", AttachmentType.IMAGE, List.of("webp"),
                    h -> ascii(h, 0, 4).equals("RIFF") && ascii(h, 8, 4).equals("WEBP")),
            new Format("image/heic", AttachmentType.IMAGE, List.of("heic"),
                    h -> ascii(h, 4, 4).equals("ftyp") && HEIC_BRANDS.contains(ascii(h, 8, 4))),
            new Format("video/mp4", AttachmentType.VIDEO, List.of("mp4"),
                    h -> ascii(h, 4, 4).equals("ftyp")),
            new Format("video/quicktime", AttachmentType.VIDEO, List.of("mov"),
                    h -> ascii(h, 4, 4).equals("ftyp") || MOV_ATOMS.contains(ascii(h, 4, 4))),
            new Format("application/pdf", AttachmentType.DOCUMENT, List.of("pdf"),
                    h -> ascii(h, 0, 4).equals("%PDF")));

    /**
     * Kiểm tra file và trả định dạng đã chuẩn hóa. Sai MIME / đuôi / byte đầu → ERR_FILE_INVALID_TYPE;
     * quá dung lượng của loại đó → ERR_FILE_TOO_LARGE.
     */
    Format resolve(String declaredContentType, String originalFilename, long size, byte[] header) {
        String contentType = declaredContentType != null ? declaredContentType.toLowerCase(Locale.ROOT).trim() : "";
        Format format = FORMATS.stream()
                .filter(f -> f.contentType().equals(contentType))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ERR_FILE_INVALID_TYPE, declaredContentType));

        String extension = extensionOf(originalFilename);
        if (!format.extensions().contains(extension)) {
            throw new AppException(ErrorCode.ERR_FILE_INVALID_TYPE, "đuôi ." + extension + " không khớp " + contentType);
        }
        if (size <= 0 || header == null || !format.signature().test(header)) {
            throw new AppException(ErrorCode.ERR_FILE_INVALID_TYPE, "nội dung file không đúng định dạng " + contentType);
        }
        if (size > MAX_SIZE.get(format.type())) {
            throw new AppException(ErrorCode.ERR_FILE_TOO_LARGE);
        }
        return format;
    }

    /** MIME của file đã lưu, suy từ đuôi của key (key do hệ thống sinh nên luôn có đuôi chuẩn). */
    Optional<String> contentTypeOfKey(String key) {
        String extension = extensionOf(key);
        return FORMATS.stream()
                .filter(f -> f.extensions().contains(extension))
                .map(Format::contentType)
                .findFirst();
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static boolean startsWith(byte[] header, int offset, int... expected) {
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] header, int offset, int length) {
        if (header.length < offset + length) {
            return "";
        }
        return new String(Arrays.copyOfRange(header, offset, offset + length), StandardCharsets.US_ASCII);
    }
}
