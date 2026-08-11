package com.example.hitmodel.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HitModelResp {
    private String code;    // 200 成功，500 失败
    private String message; // 提示信息
    private Object data;    // 业务数据

    public static HitModelResp success(String message) {
        return new HitModelResp("200", message, null);
    }

    public static HitModelResp success(String message, Object data) {
        return new HitModelResp("200", message, data);
    }

    public static HitModelResp error(String message) {
        return new HitModelResp("500", message, null);
    }
}