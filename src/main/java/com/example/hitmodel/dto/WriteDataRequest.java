package com.example.hitmodel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 对应接口 3.2 数据写入 OPC 的请求体
 */
@Data
public class WriteDataRequest {
    /**
     * 模型编码
     */
    @NotBlank(message = "modelCode 不能为空")
    private String modelCode;

    /**
     * 写入 OPC 的数值
     */
    @NotNull(message = "writeValue 不能为空")
    private Double writeValue;
}
