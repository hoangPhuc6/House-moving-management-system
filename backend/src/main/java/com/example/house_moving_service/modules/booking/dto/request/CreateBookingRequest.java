package com.example.house_moving_service.modules.booking.dto.request;

import com.example.house_moving_service.modules.pricing.dto.request.RouteDto;
import com.example.house_moving_service.modules.pricing.dto.request.ScheduleDto;
import com.example.house_moving_service.modules.pricing.dto.request.ServiceItemDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBookingRequest {

    @NotBlank(message = "Tên người liên hệ không được để trống")
    @Size(max = 255, message = "Tên người liên hệ không được vượt quá 255 ký tự")
    private String contactName;

    @NotBlank(message = "Số điện thoại liên hệ không được để trống")
    @Pattern(
            regexp = "^(0|\\+84)[3|5|7|8|9][0-9]{8}$",
            message = "Số điện thoại liên hệ không đúng định dạng Việt Nam"
    )
    private String contactPhone;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String note;

    @NotNull(message = "Thông tin lịch trình không được để trống")
    @Valid
    private ScheduleDto schedule;

    @NotNull(message = "Thông tin lộ trình không được để trống")
    @Valid
    private RouteDto route;

    @NotEmpty(message = "Danh sách dịch vụ không được để trống")
    @Valid
    private List<ServiceItemDto> serviceItems;

    @Valid
    @Builder.Default
    private List<BookingItemRequest> items = new ArrayList<>();

    private String voucherCode;
}

