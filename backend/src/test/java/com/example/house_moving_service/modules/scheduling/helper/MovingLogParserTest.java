package com.example.house_moving_service.modules.scheduling.helper;

import com.example.house_moving_service.modules.scheduling.dto.response.MovingLogEntry;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingTimelineEntry;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovingLogParserTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 2, 8, 5);

    @Test
    @DisplayName("parse: tách thời điểm, tag, người ghi, nội dung; dòng system không có actor")
    void testParse() {
        MovingSchedule s = new MovingSchedule();
        s.appendNote("START", 8L, "Bắt đầu chuyến", T0);
        s.appendNote("ARRIVED_PICKUP", 8L, "Đã đến điểm lấy hàng", T0.plusMinutes(20));
        s.appendNote("CANCEL", null, "lý do", T0.plusMinutes(30));

        List<MovingLogEntry> entries = MovingLogParser.parse(s.getNote());

        assertThat(entries).containsExactly(
                new MovingLogEntry(T0, "START", 8L, "Bắt đầu chuyến"),
                new MovingLogEntry(T0.plusMinutes(20), "ARRIVED_PICKUP", 8L, "Đã đến điểm lấy hàng"),
                new MovingLogEntry(T0.plusMinutes(30), "CANCEL", null, "lý do"));
    }

    @Test
    @DisplayName("parse: ghi chú nhiều dòng được nối vào dòng trước; dòng tự do ở đầu giữ nguyên")
    void testParse_MultilineAndFreeText() {
        String note = "ghi chú tay\n[2026-11-02 08:05][START][user#8] dòng 1\ndòng 2";

        assertThat(MovingLogParser.parse(note)).containsExactly(
                new MovingLogEntry(null, null, null, "ghi chú tay"),
                new MovingLogEntry(T0, "START", 8L, "dòng 1\ndòng 2"));
        assertThat(MovingLogParser.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("timeline: chỉ START, các mốc, COMPLETE; bỏ nội dung và tag khác")
    void testTimeline() {
        MovingSchedule s = new MovingSchedule();
        s.appendNote("START", 8L, "x", T0);
        s.appendNote("NOTE", 1L, "nội bộ", T0.plusMinutes(1));
        s.appendNote("OTHER", 8L, "kẹt xe", T0.plusMinutes(2));
        s.appendNote("COMPLETE", 8L, "y", T0.plusMinutes(3));

        assertThat(MovingLogParser.timeline(s.getNote())).containsExactly(
                new MovingTimelineEntry("START", T0),
                new MovingTimelineEntry("OTHER", T0.plusMinutes(2)),
                new MovingTimelineEntry("COMPLETE", T0.plusMinutes(3)));
    }

    @Test
    @DisplayName("delayMinutes: ưu tiên giờ kết thúc, rồi giờ bắt đầu, chưa có thì null")
    void testDelayMinutes() {
        MovingSchedule s = MovingSchedule.builder()
                .scheduledStartAt(T0).scheduledEndAt(T0.plusHours(4)).build();
        assertThat(MovingLogParser.delayMinutes(s)).isNull();

        s.setActualStartAt(T0.minusMinutes(10));
        assertThat(MovingLogParser.delayMinutes(s)).isEqualTo(-10L);

        s.setActualEndAt(T0.plusHours(4).plusMinutes(25));
        assertThat(MovingLogParser.delayMinutes(s)).isEqualTo(25L);
    }
}
