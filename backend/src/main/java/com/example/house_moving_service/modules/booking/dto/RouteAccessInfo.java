package com.example.house_moving_service.modules.booking.dto;

/**
 Điều kiện ra vào tại một đầu (điểm đi hoặc điểm đến): tầng, thang máy, cự ly đi bộ trong hẻm.
 Trong DB được lưu dạng chuỗi ở booking_routes.pickup_note / dropoff_note (xem RouteAccessNoteCodec).
 */
public record RouteAccessInfo(int floorNumber, boolean hasElevator, int walkingDistanceMeters, String extraNote) {

    public static final RouteAccessInfo EMPTY = new RouteAccessInfo(0, false, 0, null);
}
