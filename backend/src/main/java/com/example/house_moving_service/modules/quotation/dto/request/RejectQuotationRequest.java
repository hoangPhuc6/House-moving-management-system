package com.example.house_moving_service.modules.quotation.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectQuotationRequest {

    @Size(max = 1000, message = "Lý do từ chối không được vượt quá 1000 ký tự")
    private String reason;
}
