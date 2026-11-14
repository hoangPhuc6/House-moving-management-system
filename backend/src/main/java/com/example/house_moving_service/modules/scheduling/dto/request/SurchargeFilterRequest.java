package com.example.house_moving_service.modules.scheduling.dto.request;

import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Bộ lọc phụ phí cho điều phối (query param). Không truyền status thì mặc định PROPOSED; from / to lọc theo ngày đề xuất. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurchargeFilterRequest {

    private SurchargeStatus status;

    private SurchargeType surchargeType;

    private Integer bookingId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
}
