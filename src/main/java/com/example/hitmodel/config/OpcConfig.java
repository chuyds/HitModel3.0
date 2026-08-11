package com.example.hitmodel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 将 yml 中的 opc 配置映射为对象
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "opc")
public class OpcConfig {
    private String host;
    private String domain;
    private String user;
    private String password;
    private String clsid;

    /**
     * 对应 yml 中的 model-tags 映射表
     */
    private Map<String, String> modelTags;

    /**
     * OPC 心跳配置
     */
    private Heartbeat heartbeat = new Heartbeat();

    /**
     * 实时 OPC 采集广播配置
     */
    private Realtime realtime = new Realtime();

    /**
     * Python 写回 OPC 的 socket 服务配置
     */
    private WriteServer writeServer = new WriteServer();

    @Data
    public static class Heartbeat {
        private boolean enabled = true;
        private String tag;
        private long intervalMs = 1000;
    }

    @Data
    public static class Realtime {
        private boolean enabled = true;
        private long collectIntervalMs = 1000;
        private int broadcastPort = 50008;
        private long retryDelayMs = 5000;
    }

    @Data
    public static class WriteServer {
        private boolean enabled = true;
        private int port = 50009;
        private long retryDelayMs = 5000;
    }
}
