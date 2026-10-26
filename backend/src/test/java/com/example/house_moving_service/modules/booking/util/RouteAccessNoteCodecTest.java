package com.example.house_moving_service.modules.booking.util;

import com.example.house_moving_service.modules.booking.dto.RouteAccessInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RouteAccessNoteCodecTest {

    @Test
    @DisplayName("format: Should produce the legacy string when there is no extra note")
    void testFormat_WithoutExtraNote() {
        String note = RouteAccessNoteCodec.format(new RouteAccessInfo(3, false, 15, null));

        assertThat(note).isEqualTo("Tầng: 3, Thang máy: Không, Cự ly hẻm: 15m");
    }

    @Test
    @DisplayName("format: Should append extra note in parentheses")
    void testFormat_WithExtraNote() {
        String note = RouteAccessNoteCodec.format(new RouteAccessInfo(12, true, 0, "Có hầm đỗ xe tải"));

        assertThat(note).isEqualTo("Tầng: 12, Thang máy: Có, Cự ly hẻm: 0m (Có hầm đỗ xe tải)");
    }

    @Test
    @DisplayName("format: Should ignore blank extra note and return null for null info")
    void testFormat_BlankExtraNoteAndNull() {
        assertThat(RouteAccessNoteCodec.format(new RouteAccessInfo(1, false, 0, "   ")))
                .isEqualTo("Tầng: 1, Thang máy: Không, Cự ly hẻm: 0m");
        assertThat(RouteAccessNoteCodec.format(null)).isNull();
    }

    @Test
    @DisplayName("parse: Should read the standard string")
    void testParse_StandardString() {
        RouteAccessInfo info = RouteAccessNoteCodec.parse("Tầng: 8, Thang máy: Có, Cự ly hẻm: 0m");

        assertThat(info).isEqualTo(new RouteAccessInfo(8, true, 0, null));
    }

    @Test
    @DisplayName("parse: Should keep the free-text note from V9 seed data")
    void testParse_SeedDataWithExtraNote() {
        RouteAccessInfo info = RouteAccessNoteCodec.parse(
                "Tầng: 3, Thang máy: Không, Cự ly hẻm: 15m (Phải vác thang bộ từ lầu 3 xuống)");

        assertThat(info).isEqualTo(new RouteAccessInfo(3, false, 15, "Phải vác thang bộ từ lầu 3 xuống"));
    }

    @Test
    @DisplayName("parse: Should keep nested parentheses inside the extra note")
    void testParse_NestedParentheses() {
        RouteAccessInfo info = RouteAccessNoteCodec.parse(
                "Tầng: 1, Thang máy: Không, Cự ly hẻm: 60m (Hẻm nhỏ (xe ba gác) không vào được)");

        assertThat(info.extraNote()).isEqualTo("Hẻm nhỏ (xe ba gác) không vào được");
    }

    @Test
    @DisplayName("parse: Should not strip parentheses that are not a single enclosing pair")
    void testParse_TwoSeparateParenthesisGroups() {
        RouteAccessInfo info = RouteAccessNoteCodec.parse("Tầng: 1, Thang máy: Không, Cự ly hẻm: 0m (cổng A) và (cổng B)");

        assertThat(info.extraNote()).isEqualTo("(cổng A) và (cổng B)");
    }

    @Test
    @DisplayName("parse: Should return EMPTY for null or blank input")
    void testParse_NullOrBlank() {
        assertThat(RouteAccessNoteCodec.parse(null)).isEqualTo(RouteAccessInfo.EMPTY);
        assertThat(RouteAccessNoteCodec.parse("  ")).isEqualTo(RouteAccessInfo.EMPTY);
    }

    @Test
    @DisplayName("parse: Should not throw on unknown format and keep the whole text as extra note")
    void testParse_UnknownFormat() {
        RouteAccessInfo info = RouteAccessNoteCodec.parse("Nhà trong hẻm sâu, gọi trước khi tới");

        assertThat(info).isEqualTo(new RouteAccessInfo(0, false, 0, "Nhà trong hẻm sâu, gọi trước khi tới"));
    }

    @Test
    @DisplayName("parse: Should treat numbers overflowing int as unknown format")
    void testParse_NumberOverflow() {
        String note = "Tầng: 99999999999, Thang máy: Có, Cự ly hẻm: 0m";

        assertThat(RouteAccessNoteCodec.parse(note)).isEqualTo(new RouteAccessInfo(0, false, 0, note));
    }

    @ParameterizedTest
    @MethodSource("roundTripCases")
    @DisplayName("parse(format(x)) should equal x")
    void testRoundTrip(RouteAccessInfo info) {
        assertThat(RouteAccessNoteCodec.parse(RouteAccessNoteCodec.format(info))).isEqualTo(info);
    }

    static Stream<RouteAccessInfo> roundTripCases() {
        return Stream.of(
                new RouteAccessInfo(0, false, 0, null),
                new RouteAccessInfo(5, true, 120, null),
                new RouteAccessInfo(2, false, 30, "Đường một chiều, chỉ vào được sau 22h"),
                new RouteAccessInfo(7, true, 0, "Thang máy nhỏ (tối đa 2 người)")
        );
    }
}
