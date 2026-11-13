package com.example.house_moving_service.modules.scheduling.helper;

import com.example.house_moving_service.modules.scheduling.dto.response.MovingLogEntry;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingTimelineEntry;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.ProgressMilestone;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc nhật ký chuyến (moving_schedules.note). Mỗi dòng có dạng "[yyyy-MM-dd HH:mm][TAG][user#id] nội dung"
 * (xem MovingSchedule#appendNote). Dòng không khớp định dạng (vd. ghi chú nhiều dòng) được nối vào dòng trước đó.
 */
public final class MovingLogParser {

    public static final String TAG_START = "START";
    public static final String TAG_COMPLETE = "COMPLETE";

    private static final Pattern LINE = Pattern.compile(
            "^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})]\\[([A-Z_]+)]\\[(?:user#(\\d+)|system)] ?(.*)$");

    // Tag được đưa lên dòng thời gian: START → các mốc tiến độ → COMPLETE
    private static final Set<String> TIMELINE_TAGS = buildTimelineTags();

    private MovingLogParser() {
    }

    public static List<MovingLogEntry> parse(String note) {
        List<MovingLogEntry> entries = new ArrayList<>();
        if (note == null || note.isBlank()) {
            return entries;
        }
        for (String line : note.split("\\R")) {
            Matcher m = LINE.matcher(line);
            LocalDateTime at = m.matches() ? parseTime(m.group(1)) : null;
            if (at != null) {
                Long actor = m.group(3) != null ? Long.valueOf(m.group(3)) : null;
                entries.add(new MovingLogEntry(at, m.group(2), actor, m.group(4)));
            } else if (!entries.isEmpty()) {
                MovingLogEntry last = entries.removeLast();
                entries.add(new MovingLogEntry(last.at(), last.tag(), last.actorUserId(), last.content() + "\n" + line));
            } else if (!line.isBlank()) {
                entries.add(new MovingLogEntry(null, null, null, line));
            }
        }
        return entries;
    }

    /** Dòng thời gian cho khách: chỉ tên mốc và thời điểm, bỏ nội dung và người ghi. */
    public static List<MovingTimelineEntry> timeline(String note) {
        return parse(note).stream()
                .filter(e -> e.tag() != null && TIMELINE_TAGS.contains(e.tag()))
                .map(e -> new MovingTimelineEntry(e.tag(), e.at()))
                .toList();
    }

    /**
     * Độ trễ (phút): có actualEndAt thì so với scheduledEndAt; không thì có actualStartAt thì so với scheduledStartAt;
     * chưa có cả hai thì null. Âm = sớm hơn giờ hẹn.
     */
    public static Long delayMinutes(MovingSchedule schedule) {
        if (schedule.getActualEndAt() != null && schedule.getScheduledEndAt() != null) {
            return Duration.between(schedule.getScheduledEndAt(), schedule.getActualEndAt()).toMinutes();
        }
        if (schedule.getActualStartAt() != null && schedule.getScheduledStartAt() != null) {
            return Duration.between(schedule.getScheduledStartAt(), schedule.getActualStartAt()).toMinutes();
        }
        return null;
    }

    private static LocalDateTime parseTime(String text) {
        try {
            return LocalDateTime.parse(text, MovingSchedule.NOTE_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Set<String> buildTimelineTags() {
        Set<String> tags = new HashSet<>();
        tags.add(TAG_START);
        tags.add(TAG_COMPLETE);
        Arrays.stream(ProgressMilestone.values()).map(Enum::name).forEach(tags::add);
        return Set.copyOf(tags);
    }
}
