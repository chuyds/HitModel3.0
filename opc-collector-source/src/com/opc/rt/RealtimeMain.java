package com.opc.rt;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class RealtimeMain {

    // =========================
    // 广播客户端列表
    // =========================
    private static final List<PrintWriter> CLIENTS =
            new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            // 1. 加载配置
            Properties cfg = new Properties();
            cfg.load(new FileInputStream("config/opc-read.properties"));

            long interval =
                    Long.parseLong(cfg.getProperty("collect.interval.ms", "1000"));

            int listenPort =
                    Integer.parseInt(cfg.getProperty("python.port", "50008"));

            // 2. 初始化 OPC
            DBUtils db = new DBUtils(
                    cfg.getProperty("db.url"),
                    cfg.getProperty("db.user"),
                    cfg.getProperty("db.password")
            );
            List<String> tags = db.loadTags();

            OPCReadClient opc = new OPCReadClient(
                    cfg.getProperty("opc.host"),
                    cfg.getProperty("opc.domain"),
                    cfg.getProperty("opc.user"),
                    cfg.getProperty("opc.password"),
                    cfg.getProperty("opc.clsid")
            );
            opc.connect(tags);

            System.out.println("[READ] OPC 初始化完成（一次性）");

            // 3. 启动 Python 订阅监听
            ServerSocket server = new ServerSocket(listenPort);
            System.out.println("[BROADCAST] 等待 Python 连接，端口 = " + listenPort);

            new Thread(() -> {
                while (true) {
                    try {
                        Socket sock = server.accept();
                        PrintWriter out = new PrintWriter(
                                new OutputStreamWriter(
                                        sock.getOutputStream(), "UTF-8"),
                                true
                        );
                        CLIENTS.add(out);
                        System.out.println(
                                "[BROADCAST] Python 已连接: "
                                        + sock.getRemoteSocketAddress()
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, "python-accept-thread").start();

            // 4. 实时采集 + 广播
            SimpleDateFormat sdf =
                    new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

            while (true) {
                if (!opc.isConnected()) {
                    System.err.println("[READ] OPC 断开，退出");
                    break;
                }

                Map<String, Object> vals = opc.read();

                StringBuilder json = new StringBuilder();
                json.append("{\"time\":\"")
                        .append(sdf.format(new Date()))
                        .append("\"");

                for (String t : tags) {
                    Object v = vals.get(t);
                    json.append(",\"")
                            .append(t)
                            .append("\":\"")
                            .append(v == null ? "" : v.toString())
                            .append("\"");
                }
                json.append("}");

                String msg = json.toString();

                // ---- 广播给所有 Python ----
                for (PrintWriter out : CLIENTS) {
                    try {
                        out.println(msg);
                    } catch (Exception e) {
                        CLIENTS.remove(out);
                    }
                }

                System.out.println("[BROADCAST] " + msg);

                Thread.sleep(interval);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
