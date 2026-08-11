package com.example.hitmodel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对应数据读取 OPC 的请求体
 */
@Data
public class ReadDataRequest {
    /**
     * 模型编码
     */
    @NotBlank(message = "modelCode 不能为空")
    private String modelCode;
}
