package com.opc.bridge;

import com.opc.write.OPCWriteClient;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class WriteServer {

    public static void main(String[] args) {

        int port = Integer.parseInt(
                System.getProperty("write.port", "50009")
        );

        OPCWriteClient client;
        try {
            client = new OPCWriteClient();
            client.connect();
        } catch (Exception e) {
            System.err.println("[WRITE-SERVER] OPC 初始化失败");
            e.printStackTrace();
            return;
        }

        try (ServerSocket server = new ServerSocket(port)) {

            System.out.println("[WRITE-SERVER] 启动成功，监听端口 " + port);

            while (!shouldStop()) {
                Socket sock = server.accept();
                System.out.println("[WRITE-SERVER] Python 已连接: "
                        + sock.getRemoteSocketAddress());

                new Thread(() -> handle(sock, client)).start();
            }

        } catch (Exception e) {
            System.err.println("[WRITE-SERVER] 服务异常退出");
            e.printStackTrace();
        } finally {
            client.disconnect();
            System.out.println("[WRITE-SERVER] OPC 已断开");
        }
    }

    private static void handle(Socket sock, OPCWriteClient client) {
        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                sock.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(
                                sock.getOutputStream(), StandardCharsets.UTF_8),
                        true)
        ) {
            String line;
            while ((line = in.readLine()) != null && !shouldStop()) {

                line = line.trim();
                if (line.isEmpty()) continue;

                // 协议：tag,value,type
                String[] p = line.split(",", 3);
                if (p.length < 2) {
                    out.println("FAIL:BAD_FORMAT");
                    continue;
                }

                String tag = p[0].trim();
                String val = p[1].trim();
                String type = (p.length == 3)
                        ? p[2].trim().toLowerCase()
                        : "double";

                try {
                    boolean ok = client.write(tag, val, type);
                    out.println(ok ? "OK" : "FAIL:WRITE_FALSE");
                } catch (Exception e) {
                    out.println("FAIL:" + e.getClass().getSimpleName());
                }
            }
        } catch (Exception e) {
            System.out.println("[WRITE-SERVER] 客户端断开");
        }
    }

    private static boolean shouldStop() {
        return new File("stop.flag").exists();
    }
}
