package com.example.house_moving_service.modules.survey.service;

import java.time.LocalDateTime;

/**
 * Module survey định nghĩa, module Lập lịch cài đặt (dependency inversion: survey không import module Lập lịch,
 * vì module đó đã gọi survey qua SurveyStaffScheduleService).
 * Survey inject không bắt buộc: chưa có bean cài đặt thì coi như nhân viên không bận việc khác.
 * Bean cài đặt không được phụ thuộc ngược lại SurveyStaffScheduleService để tránh vòng bean.
 */
public interface StaffExternalScheduleChecker {

    boolean isBusy(Long staffId, LocalDateTime start, LocalDateTime end);
}
