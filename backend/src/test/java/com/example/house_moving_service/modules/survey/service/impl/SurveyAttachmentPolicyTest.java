package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.survey.enums.AttachmentType;
import com.example.house_moving_service.modules.survey.service.impl.SurveyAttachmentPolicy.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurveyAttachmentPolicyTest {

    private static final long MB = 1024L * 1024;

    static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1);
    static final byte[] PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R');
    static final byte[] PDF = ascii("%PDF-1.7\n%âãÏÓ\n1");
    static final byte[] MP4 = header(0, 0, 0, 0x18, "ftypisom");
    static final byte[] HEIC = header(0, 0, 0, 0x18, "ftypheic");
    static final byte[] WEBP = ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ");
    // Byte đầu của file .exe (Windows): "MZ"
    static final byte[] EXE = bytes('M', 'Z', 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, 0xFF, 0xFF, 0, 0);

    private final SurveyAttachmentPolicy policy = new SurveyAttachmentPolicy();

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] ascii(String text) {
        byte[] raw = text.getBytes(StandardCharsets.ISO_8859_1);
        byte[] result = new byte[16];
        System.arraycopy(raw, 0, result, 0, Math.min(raw.length, 16));
        return result;
    }

    private static byte[] header(int b0, int b1, int b2, int b3, String rest) {
        byte[] result = ascii("____" + rest);
        result[0] = (byte) b0;
        result[1] = (byte) b1;
        result[2] = (byte) b2;
        result[3] = (byte) b3;
        return result;
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    @Test
    @DisplayName("Các định dạng hợp lệ → đúng loại và MIME chuẩn; đuôi viết hoa vẫn được; .jpeg sinh key đuôi .jpg")
    void testValidFormats() {
        Format jpeg = policy.resolve("image/jpeg", "Phong_khach.JPEG", 2 * MB, JPEG);
        assertThat(jpeg.type()).isEqualTo(AttachmentType.IMAGE);
        assertThat(jpeg.defaultExtension()).isEqualTo("jpg");

        assertThat(policy.resolve("image/png", "a.png", MB, PNG).type()).isEqualTo(AttachmentType.IMAGE);
        assertThat(policy.resolve("image/webp", "a.webp", MB, WEBP).type()).isEqualTo(AttachmentType.IMAGE);
        assertThat(policy.resolve("image/heic", "IMG_0001.HEIC", MB, HEIC).type()).isEqualTo(AttachmentType.IMAGE);
        assertThat(policy.resolve("video/mp4", "quay_nha.mp4", 40 * MB, MP4).type()).isEqualTo(AttachmentType.VIDEO);
        assertThat(policy.resolve("video/quicktime", "a.mov", 40 * MB, MP4).type()).isEqualTo(AttachmentType.VIDEO);
        assertThat(policy.resolve("application/pdf", "ban_ve.pdf", MB, PDF).type()).isEqualTo(AttachmentType.DOCUMENT);
    }

    @Test
    @DisplayName("MIME không thuộc bảng (vd. exe, zip, không có) → ERR_FILE_INVALID_TYPE")
    void testUnsupportedMime() {
        assertError(() -> policy.resolve("application/x-msdownload", "a.exe", MB, EXE), ErrorCode.ERR_FILE_INVALID_TYPE);
        assertError(() -> policy.resolve("application/zip", "a.zip", MB, EXE), ErrorCode.ERR_FILE_INVALID_TYPE);
        assertError(() -> policy.resolve(null, "a.jpg", MB, JPEG), ErrorCode.ERR_FILE_INVALID_TYPE);
    }

    @Test
    @DisplayName("Đuôi file không khớp MIME (khai image/jpeg nhưng tên .png / không có đuôi) → ERR_FILE_INVALID_TYPE")
    void testExtensionMismatch() {
        assertError(() -> policy.resolve("image/jpeg", "a.png", MB, JPEG), ErrorCode.ERR_FILE_INVALID_TYPE);
        assertError(() -> policy.resolve("image/jpeg", "khong_duoi", MB, JPEG), ErrorCode.ERR_FILE_INVALID_TYPE);
    }

    @Test
    @DisplayName("File .exe đổi tên thành .jpg, khai image/jpeg → byte đầu sai → ERR_FILE_INVALID_TYPE")
    void testRenamedExecutable() {
        assertError(() -> policy.resolve("image/jpeg", "anh.jpg", MB, EXE), ErrorCode.ERR_FILE_INVALID_TYPE);
        assertError(() -> policy.resolve("application/pdf", "ban_ve.pdf", MB, PNG), ErrorCode.ERR_FILE_INVALID_TYPE);
    }

    @Test
    @DisplayName("File rỗng / header quá ngắn → ERR_FILE_INVALID_TYPE")
    void testEmptyOrTruncated() {
        assertError(() -> policy.resolve("image/jpeg", "a.jpg", 0, JPEG), ErrorCode.ERR_FILE_INVALID_TYPE);
        assertError(() -> policy.resolve("image/png", "a.png", 10, bytes(0x89, 0x50)), ErrorCode.ERR_FILE_INVALID_TYPE);
    }

    @Test
    @DisplayName("Dung lượng theo loại: ảnh / PDF đúng 10MB được, hơn thì ERR_FILE_TOO_LARGE; video tới 50MB")
    void testSizeLimits() {
        assertThat(policy.resolve("image/jpeg", "a.jpg", 10 * MB, JPEG).type()).isEqualTo(AttachmentType.IMAGE);
        assertError(() -> policy.resolve("image/jpeg", "a.jpg", 10 * MB + 1, JPEG), ErrorCode.ERR_FILE_TOO_LARGE);
        assertError(() -> policy.resolve("application/pdf", "a.pdf", 10 * MB + 1, PDF), ErrorCode.ERR_FILE_TOO_LARGE);
        assertThat(policy.resolve("video/mp4", "a.mp4", 50 * MB, MP4).type()).isEqualTo(AttachmentType.VIDEO);
        assertError(() -> policy.resolve("video/mp4", "a.mp4", 50 * MB + 1, MP4), ErrorCode.ERR_FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("contentTypeOfKey: suy MIME từ đuôi của key đã lưu; đuôi lạ → rỗng")
    void testContentTypeOfKey() {
        assertThat(policy.contentTypeOfKey("surveys/7/40/abc.jpg")).contains("image/jpeg");
        assertThat(policy.contentTypeOfKey("surveys/7/40/abc.mov")).contains("video/quicktime");
        assertThat(policy.contentTypeOfKey("surveys/7/40/abc.pdf")).contains("application/pdf");
        assertThat(policy.contentTypeOfKey("surveys/7/40/abc.bin")).isEmpty();
    }
}
