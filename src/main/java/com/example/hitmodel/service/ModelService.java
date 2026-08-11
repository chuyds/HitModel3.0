package com.example.hitmodel.service;

//import com.opc.jlxg.opc.OPCDAClient;
import com.example.hitmodel.config.OpcConfig;
import com.example.hitmodel.opc.da.OPCDAClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
public class ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    private final OPCDAClient opcClient;
    private final OpcConfig opcConfig;

    // 获取 JAR 包运行的根目录
    private final String baseDir = System.getProperty("user.dir");
    // 子目录路径
    private final String subDirName = "FinalCoolingTower";

    // 直接注入 OpcConfig 和 OPCDAClient
    public ModelService(OPCDAClient opcClient, OpcConfig opcConfig) {
        this.opcClient = opcClient;
        this.opcConfig = opcConfig;

        // 使用配置类中的参数初始化 OPC 客户端
        this.opcClient.init(
                opcConfig.getHost(),
                opcConfig.getUser(),
                opcConfig.getPassword(),
                opcConfig.getDomain(),
                opcConfig.getClsid()
        );
    }

    /**
     * 接口 3.1 逻辑：启停模型
     */
    public boolean stopOrStartModel(String modelCode, Integer status) throws IOException {
        File subDir = new File(baseDir, subDirName);
        String scriptSuffix = resolveScriptSuffix(modelCode);

        switch (status) {
            case 0: // 停止测试
                runBat("stop-realtimepredict-test.bat", subDir);
                break;

            case 1: // 启动测试
                runBat("start-realtimepredict-test.bat", subDir);
                break;

            case 2: // 进入测试页面逻辑
                runBat("stop-zhonglengTower" + scriptSuffix + ".bat", subDir);
                runBat("stop-realtimepredict-test.bat", subDir);
                break;

            case 3: // 智能控制（正式启动）
                runBat("stop-zhonglengTower" + scriptSuffix + ".bat", subDir);
                runBat("stop-realtimepredict-test.bat", subDir);
                sleepQuietly(100);
                runBat("start-zhonglengTower" + scriptSuffix + ".bat", subDir);
                break;

            default:
                log.warn("收到未知模型控制指令: modelCode={}, status={}", modelCode, status);
                return false;
        }

        log.info(
                "模型控制指令执行完成: modelCode={}, status={}, statusDesc={}",
                modelCode,
                status,
                resolveStatusDesc(status)
        );
        return true;
    }

    private String resolveStatusDesc(Integer status) {
        switch (status) {
            case 0:
                return "停止测试";
            case 1:
                return "启动测试";
            case 2:
                return "停止智能控制";
            case 3:
                return "启动智能控制";
            default:
                return "未知状态";
        }
    }

    private String resolveScriptSuffix(String modelCode) {
        if ("final_cooler_temp_control_model".equals(modelCode)) {
            return "A";
        }
        if ("final_cooler_temp_control_modelB".equals(modelCode)) {
            return "B";
        }
        throw new IllegalArgumentException("stopOrStart 仅支持以下 modelCode: final_cooler_temp_control_model, final_cooler_temp_control_modelB；当前值: " + modelCode);
    }

    /**
     * 通用的脚本执行方法
     * @param batName 脚本文件名
     * @param workingDir 脚本所在的实际目录
     */
    private void runBat(String batName, File workingDir) throws IOException {
        File batFile = new File(workingDir, batName);
        if (!batFile.exists()) {
            throw new IOException("错误：找不到文件 " + batFile.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", batName);
        pb.directory(workingDir);
        pb.start();
        log.info("已启动脚本: script={}, workingDir={}", batName, workingDir.getAbsolutePath());
    }

    private void sleepQuietly(long ms) throws IOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("线程休眠被中断", e);
        }
    }

    /**
     * 接口 3.2 逻辑：数据写入 OPC
     */
    public boolean writeDataToOpc(String modelCode, Double value) {
        Map<String, String> modelTags = opcConfig.getModelTags();

        if (modelTags == null || !modelTags.containsKey(modelCode)) {
            throw new IllegalArgumentException("未找到模型编码对应的 OPC 标签映射: " + modelCode);
        }

        String tagId = modelTags.get(modelCode);
        return opcClient.write(tagId, value);
    }

    /**
     * 数据读取 OPC
     */
    public Object readDataFromOpc(String modelCode) {
        Map<String, String> modelTags = opcConfig.getModelTags();

        if (modelTags == null || !modelTags.containsKey(modelCode)) {
            throw new IllegalArgumentException("未找到模型编码对应的 OPC 标签映射: " + modelCode);
        }

        String tagId = modelTags.get(modelCode);
        return opcClient.read(tagId);
    }
}
