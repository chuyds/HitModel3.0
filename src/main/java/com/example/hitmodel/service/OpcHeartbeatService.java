package com.example.hitmodel.service;

import com.example.hitmodel.config.OpcConfig;
import com.example.hitmodel.opc.da.OPCDAClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OpcHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(OpcHeartbeatService.class);

    private final OPCDAClient opcClient;
    private final OpcConfig opcConfig;
    private boolean heartbeatValue;
    private boolean lastWriteSucceeded;
    private boolean heartbeatDisabledLogged;

    public OpcHeartbeatService(OPCDAClient opcClient, OpcConfig opcConfig) {
        this.opcClient = opcClient;
        this.opcConfig = opcConfig;
    }

    @PostConstruct
    public void init() {
        heartbeatValue = false;
        lastWriteSucceeded = true;
        heartbeatDisabledLogged = false;
    }

    @Scheduled(fixedDelayString = "${opc.heartbeat.interval-ms:1000}")
    public void writeHeartbeat() {
        OpcConfig.Heartbeat heartbeatConfig = opcConfig.getHeartbeat();
        if (!heartbeatConfig.isEnabled()) {
            if (!heartbeatDisabledLogged) {
                log.info("OPC心跳功能已关闭，跳过心跳写入");
                heartbeatDisabledLogged = true;
            }
            lastWriteSucceeded = true;
            return;
        }
        heartbeatDisabledLogged = false;

        String heartbeatTag = heartbeatConfig.getTag();
        if (heartbeatTag == null || heartbeatTag.isBlank()) {
            if (lastWriteSucceeded) {
                log.warn("OPC心跳标签未配置，跳过心跳写入");
                lastWriteSucceeded = false;
            }
            return;
        }

        boolean nextValue = !heartbeatValue;
        boolean success = opcClient.write(heartbeatTag, nextValue);

        if (success) {
            heartbeatValue = nextValue;
            if (!lastWriteSucceeded) {
                log.info("OPC心跳写入已恢复: {} = {}", heartbeatTag, heartbeatValue);
            }
            lastWriteSucceeded = true;
        } else {
            log.warn("OPC心跳写入失败，将继续重试连接并写入: {}", heartbeatTag);
            lastWriteSucceeded = false;
        }
    }
}
