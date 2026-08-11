package com.example.hitmodel.opc.da;

import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.Group;
import org.openscada.opc.lib.da.Item;
import org.openscada.opc.lib.da.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class OPCDAReadClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OPCDAReadClient.class);

    private final ConnectionInformation info = new ConnectionInformation();
    private final Map<String, Item> items = new LinkedHashMap<>();
    private ScheduledExecutorService executor;
    private Server server;
    private Group group;

    public OPCDAReadClient(String host, String domain, String user, String password, String clsid) {
        info.setHost(host);
        info.setDomain(domain);
        info.setUser(user);
        info.setPassword(password);
        info.setClsid(clsid);
    }

    public synchronized void connect(List<String> tags) throws Exception {
        close();

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "opc-read-driver");
            t.setDaemon(true);
            return t;
        });

        server = new Server(info, executor);
        server.connect();
        group = server.addGroup("READ");
        group.setActive(true);

        items.clear();
        for (String tag : tags) {
            try {
                items.put(tag, group.addItem(tag));
                log.info("OPC实时采集点位已注册: {}", tag);
            } catch (Exception e) {
                log.warn("OPC实时采集点位注册失败: tag={}, error={}", tag, e.getMessage());
            }
        }

        if (items.isEmpty()) {
            throw new IllegalStateException("没有成功注册任何OPC实时采集点位");
        }

        log.info("OPC实时采集连接成功，注册点位数: {}", items.size());
    }

    public synchronized boolean isConnected() {
        try {
            return server != null && server.getServerState() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized Map<String, Object> readAll() throws Exception {
        if (!isConnected()) {
            throw new IllegalStateException("OPC实时采集连接已断开");
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Item> entry : items.entrySet()) {
            try {
                Object value = entry.getValue().read(false).getValue().getObject();
                values.put(entry.getKey(), value);
            } catch (Exception e) {
                values.put(entry.getKey(), null);
                log.warn("OPC实时采集点位读取失败: tag={}, error={}", entry.getKey(), e.getMessage());
            }
        }
        return values;
    }

    @Override
    public synchronized void close() {
        try {
            if (server != null) {
                server.disconnect();
            }
        } catch (Exception ignored) {
        } finally {
            server = null;
            group = null;
            items.clear();
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        }
    }
}
