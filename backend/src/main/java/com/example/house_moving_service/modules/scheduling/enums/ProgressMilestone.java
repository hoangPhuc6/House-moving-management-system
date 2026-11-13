package com.example.house_moving_service.modules.scheduling.enums;

// Các mốc giám sát viên báo trong lúc chạy chuyến; tên mốc cũng là tag trong nhật ký moving_schedules.note
public enum ProgressMilestone {
    ARRIVED_PICKUP("Đã đến điểm lấy hàng"),
    LOADING_DONE("Đã xếp hàng lên xe"),
    IN_TRANSIT("Đang vận chuyển"),
    ARRIVED_DROPOFF("Đã đến điểm giao hàng"),
    UNLOADING_DONE("Đã dỡ hàng xong"),
    OTHER("Khác");

    private final String label;

    ProgressMilestone(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
