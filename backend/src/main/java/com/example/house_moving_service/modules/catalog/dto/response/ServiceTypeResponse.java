package com.example.house_moving_service.modules.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceTypeResponse {

    private Integer id;
    private String name;
    private String description;
    private Boolean isActive;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ServiceResponse> services;
}

