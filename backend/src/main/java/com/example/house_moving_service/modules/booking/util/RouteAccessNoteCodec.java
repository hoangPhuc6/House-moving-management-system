package com.example.house_moving_service.modules.booking.util;

import com.example.house_moving_service.modules.booking.dto.RouteAccessInfo;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RouteAccessNoteCodec {

    private static final String FLOOR_LABEL = "Tầng";
    private static final String ELEVATOR_LABEL = "Thang máy";
    private static final String WALKING_LABEL = "Cự ly hẻm";
    private static final String YES = "Có";
    private static final String NO = "Không";

    private static final String FORMAT = FLOOR_LABEL + ": %d, " + ELEVATOR_LABEL + ": %s, " + WALKING_LABEL + ": %dm";

    private static final Pattern PATTERN = Pattern.compile(
            FLOOR_LABEL + ":\\s*(\\d+),\\s*" + ELEVATOR_LABEL + ":\\s*(" + YES + "|" + NO + "),\\s*"
                    + WALKING_LABEL + ":\\s*(\\d+)m");


    public static String format(RouteAccessInfo info) {
        if (info == null) {
            return null;
        }
        String base = String.format(FORMAT, info.floorNumber(), info.hasElevator() ? YES : NO, info.walkingDistanceMeters());
        if (info.extraNote() == null || info.extraNote().isBlank()) {
            return base;
        }
        return base + " (" + info.extraNote().trim() + ")";
    }


    public static RouteAccessInfo parse(String note) {
        if (note == null || note.isBlank()) {
            return RouteAccessInfo.EMPTY;
        }

        Matcher matcher = PATTERN.matcher(note);
        if (!matcher.find()) {
            return new RouteAccessInfo(0, false, 0, note.trim());
        }

        int floor;
        int walking;
        try {
            floor = Integer.parseInt(matcher.group(1));
            walking = Integer.parseInt(matcher.group(3));
        } catch (NumberFormatException e) {
            // Số vượt quá int: coi như không đúng định dạng
            return new RouteAccessInfo(0, false, 0, note.trim());
        }
        boolean hasElevator = YES.equals(matcher.group(2));

        String remainder = (note.substring(0, matcher.start()) + " " + note.substring(matcher.end())).trim();
        String extraNote = stripEnclosingParentheses(remainder);

        return new RouteAccessInfo(floor, hasElevator, walking, extraNote.isEmpty() ? null : extraNote);
    }

    /** Bỏ cặp ngoặc bao ngoài, chỉ khi ngoặc mở đầu tiên khớp đúng với ngoặc đóng cuối cùng: "(a) và (b)" giữ nguyên. */
    private static String stripEnclosingParentheses(String text) {
        if (text.length() < 2 || text.charAt(0) != '(' || text.charAt(text.length() - 1) != ')') {
            return text;
        }
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && i < text.length() - 1) {
                    return text;
                }
            }
        }
        return depth == 0 ? text.substring(1, text.length() - 1).trim() : text;
    }
}
