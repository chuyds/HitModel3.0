package com.opc.rt;

import org.openscada.opc.lib.common.ConnectionInformation;
import org.openscada.opc.lib.da.*;

import java.util.*;
import java.util.concurrent.Executors;

public class OPCReadClient {

    private final ConnectionInformation ci;
    private Server server;
    private Group group;
    private final Map<String, Item> items = new HashMap<>();

    private volatile boolean connected = false;

    public OPCReadClient(String host, String domain, String user, String pwd, String clsid) {
        ci = new ConnectionInformation();
        ci.setHost(host);
        ci.setDomain(domain);
        ci.setUser(user);
        ci.setPassword(pwd);
        ci.setClsid(clsid);
    }

    /** 只允许执行一次 */
    public synchronized void connectOnce(List<String> tags) throws Exception {
        if (connected) {
            System.out.println("[READ] OPC 已连接，跳过 connect");
            return;
        }

        server = new Server(ci, Executors.newSingleThreadScheduledExecutor());
        server.connect();

        group = server.addGroup("READ");
        group.setActive(true);

        items.clear();
        for (String tag : tags) {
            try {
                items.put(tag, group.addItem(tag));
                System.out.println("[READ] 注册：" + tag);
            } catch (Exception e) {
                System.out.println("[READ-ERR] 注册失败：" + tag);
            }
        }

        connected = true;
        System.out.println("[READ] OPC 初始化完成（一次性）");
    }

    public boolean isConnected() {
        return connected;
    }

    public Map<String, Object> readAll() throws Exception {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Item> e : items.entrySet()) {
            try {
                map.put(e.getKey(),
                        e.getValue().read(false).getValue().getObject());
            } catch (Exception ex) {
                map.put(e.getKey(), null);
            }
        }
        return map;
    }

    
    public void connect(List<String> tags) throws Exception {
        connectOnce(tags);
    }

    
    public Map<String, Object> read() throws Exception {
        return readAll();
    }
}
