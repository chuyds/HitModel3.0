package com.example.hitmodel.controller;

import com.example.hitmodel.dto.HitModelResp;
import com.example.hitmodel.dto.ModelStatusRequest;
import com.example.hitmodel.dto.ReadDataRequest;
import com.example.hitmodel.dto.WriteDataRequest;
import com.example.hitmodel.service.ModelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    // 3.1 模型启动/停止接口
    @PostMapping("/stopOrStart")
    public HitModelResp stopOrStart(@Valid @RequestBody ModelStatusRequest request) {
        try {
            modelService.stopOrStartModel(request.getModelCode(), request.getStatus());
            String msg;
            Integer status = request.getStatus();
            if (status == 0) {
                msg = "模型测试已停止";
            } else if (status == 1) {
                msg = "模型测试已启动";
            } else if (status == 2) {
                msg = "智能控制停止";
            } else if (status == 3) {
                msg = "智能控制启动，模型开始执行预测";
            } else {
                msg = "未知指令，操作失败";
            }
            return HitModelResp.success(msg);
        } catch (Exception e) {
            return HitModelResp.error("操作失败: " + e.getMessage());
        }
    }

    // 3.2 数据写入 OPC 接口
    @PostMapping("/writeData")
    public HitModelResp writeData(@Valid @RequestBody WriteDataRequest request) {
        try {
            boolean success = modelService.writeDataToOpc(request.getModelCode(), request.getWriteValue());

            if (success) {
                return HitModelResp.success("写入成功");
            } else {
                return HitModelResp.error("OPC 写入失败，请检查点位连接");
            }
        } catch (Exception e) {
            return HitModelResp.error("内部错误: " + e.getMessage());
        }
    }

    // 3.3 数据读取 OPC 接口
    @PostMapping("/readData")
    public HitModelResp readData(@Valid @RequestBody ReadDataRequest request) {
        try {
            Object value = modelService.readDataFromOpc(request.getModelCode());
            return HitModelResp.success("读取成功", value);
        } catch (Exception e) {
            return HitModelResp.error("读取失败: " + e.getMessage());
        }
    }
}

