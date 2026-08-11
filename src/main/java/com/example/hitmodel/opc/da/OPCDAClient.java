package com.example.hitmodel.opc.da;

import jakarta.annotation.PreDestroy;
import org.jinterop.dcom.core.JIVariant;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.Group;
import org.openscada.opc.lib.da.Item;
import org.openscada.opc.lib.da.ItemState;
import org.openscada.opc.lib.da.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Component
public class OPCDAClient {
    private static final Logger log = LoggerFactory.getLogger(OPCDAClient.class);
    private final ConnectionInformation info = new ConnectionInformation();
    private Server server;
    private Group group;
    private final Map<String, Item> itemCache = new ConcurrentHashMap<>();

    public void init(String host, String user, String password, String domain, String clsid) {
        info.setHost(host);
        info.setDomain(domain);
        info.setUser(user);
        info.setPassword(password);
        info.setClsid(clsid);
    }

    private boolean isConnected() {
        try {
            return server != null && server.getServerState() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void connect() throws Exception {
        if (isConnected()) {
            return;
        }
        server = new Server(info, Executors.newSingleThreadScheduledExecutor());
        server.connect();
        group = server.addGroup("WRITE_GROUP");
        group.setActive(true);
        itemCache.clear();
        log.info("OPC驱动连接成功");
    }

    private synchronized Item getItem(String id) throws Exception {
        if (!itemCache.containsKey(id)) {
            Item item = group.addItem(id);
            itemCache.put(id, item);
        }
        return itemCache.get(id);
    }

    public synchronized boolean write(String id, Object value) {
        try {
            if (!isConnected()) {
                connect();
            }
            Item item = getItem(id);
            JIVariant variant;

            if (value instanceof Double) {
                variant = new JIVariant((Double) value, false);
            } else if (value instanceof Float) {
                variant = new JIVariant((Float) value, false);
            } else if (value instanceof Integer) {
                variant = new JIVariant((Integer) value);
            } else if (value instanceof Boolean) {
                variant = new JIVariant((Boolean) value, false);
            } else {
                variant = new JIVariant(((Number) value).doubleValue(), false);
            }

            item.write(variant);
            Thread.sleep(100);
            item.read(false);
            return true;
        } catch (Exception e) {
            log.error("OPC写入失败: tag={}, value={}, error={}", id, value, e.getMessage(), e);
            return false;
        }
    }

    public synchronized Object read(String id) {
        try {
            if (!isConnected()) {
                connect();
            }
            Item item = getItem(id);
            ItemState state = item.read(false);
            return state.getValue().getObject();
        } catch (Exception e) {
            log.error("OPC读取失败: tag={}, error={}", id, e.getMessage(), e);
            throw new RuntimeException("OPC读取失败: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    public void disconnect() {
        try {
            if (server != null) {
                server.disconnect();
            }
        } catch (Exception ignored) {
        }
    }
}
