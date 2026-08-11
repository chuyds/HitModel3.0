package com.example.hitmodel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ModelStatusRequest {
    @NotBlank(message = "modelCode 不能为空")
    private String modelCode;

    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 只能是 0~3")
    @Max(value = 3, message = "status 只能是 0~3")
    private Integer status;
}
