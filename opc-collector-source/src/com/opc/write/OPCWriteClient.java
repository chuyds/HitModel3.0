package com.opc.write;

import org.jinterop.dcom.core.JIVariant;
import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.*;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class OPCWriteClient {

    private ConnectionInformation info;
    private Server server;
    private Group group;
    private final Map<String, Item> itemCache = new ConcurrentHashMap<>();

    /* ================== 原有构造（Runner / 旧程序用） ================== */

    public OPCWriteClient(
            String host,
            String user,
            String password,
            String domain,
            String clsid
    ) {
        info = new ConnectionInformation();
        info.setHost(host);
        info.setUser(user);
        info.setPassword(password);
        info.setDomain(domain);
        info.setClsid(clsid);
    }

    /* ================== 新增构造（WriteServer 用） ================== */

    public OPCWriteClient() throws Exception {
        Properties p = ConfigLoader.loadProperties("config/opc-write.properties");

        info = new ConnectionInformation();
        info.setHost(p.getProperty("opc.host"));
        info.setUser(p.getProperty("opc.user"));
        info.setPassword(p.getProperty("opc.password"));
        info.setDomain(p.getProperty("opc.domain"));
        info.setClsid(p.getProperty("opc.clsid"));
    }

    /* ================== 连接管理 ================== */

    public synchronized void connect() throws Exception {
        if (isConnected()) return;

        server = new Server(info, Executors.newSingleThreadScheduledExecutor());
        server.connect();
        group = server.addGroup("WRITE");
        group.setActive(true);

        itemCache.clear();
        System.out.println("[WRITE] OPC connected");
    }

    public synchronized void disconnect() {
        try {
            if (server != null) server.disconnect();
        } catch (Exception ignored) {}
        server = null;
        group = null;
        itemCache.clear();
    }

    public boolean isConnected() {
        try {
            return server != null && server.getServerState() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /* ================== 原有写接口（必须保留） ================== */

    public synchronized boolean write(String tag, Object value) throws Exception {
        if (!isConnected()) connect();

        Item item = getItem(tag);
        JIVariant v;

        if (value instanceof JIVariant) {
            v = (JIVariant) value;
        } else if (value instanceof Float) {
            v = new JIVariant((Float) value);
        } else if (value instanceof Double) {
            v = new JIVariant((Double) value);
        } else if (value instanceof Integer) {
            v = new JIVariant((Integer) value);
        } else if (value instanceof Boolean) {
            v = new JIVariant((Boolean) value);
        } else {
            v = new JIVariant(Double.parseDouble(value.toString()));
        }

        item.write(v);
        return true;
    }

    /* ================== 新接口（Python 用） ================== */

    public synchronized boolean write(String tag, String valStr, String type) throws Exception {
        type = type.toLowerCase();
        JIVariant v;

        switch (type) {
            case "float":
            case "real":
                v = new JIVariant(Float.parseFloat(valStr));
                break;
            case "int":
                v = new JIVariant(Integer.parseInt(valStr));
                break;
            case "bool":
            case "boolean":
                v = new JIVariant(parseBool(valStr));
                break;
            default:
                v = new JIVariant(Double.parseDouble(valStr));
        }

        return write(tag, v);
    }

    /* ================== 内部工具 ================== */

    private synchronized Item getItem(String tag) throws Exception {
        Item item = itemCache.get(tag);
        if (item == null) {
            item = group.addItem(tag);
            itemCache.put(tag, item);
        }
        return item;
    }

    private boolean parseBool(String s) {
        s = s.trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("on");
    }
}
