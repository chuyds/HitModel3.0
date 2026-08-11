package com.example.hitmodel.service;

import com.example.hitmodel.config.OpcConfig;
import com.example.hitmodel.opc.da.OPCDAClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OpcWriteSocketServerService {

    private static final Logger log = LoggerFactory.getLogger(OpcWriteSocketServerService.class);

    private final OpcConfig opcConfig;
    private final OPCDAClient opcClient;

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private ExecutorService serverExecutor;
    private ExecutorService clientExecutor;

    public OpcWriteSocketServerService(OpcConfig opcConfig, OPCDAClient opcClient) {
        this.opcConfig = opcConfig;
        this.opcClient = opcClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!opcConfig.getWriteServer().isEnabled()) {
            log.info("Python写回OPC服务已关闭");
            return;
        }
        if (running) {
            return;
        }

        running = true;
        serverExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("opc-write-server"));
        clientExecutor = Executors.newCachedThreadPool(namedThreadFactory("opc-write-client"));
        serverExecutor.submit(this::runServerLoop);
        log.info("Python写回OPC服务正在启动: port={}", opcConfig.getWriteServer().getPort());
    }

    private void runServerLoop() {
        int port = opcConfig.getWriteServer().getPort();
        long retryDelay = Math.max(1000, opcConfig.getWriteServer().getRetryDelayMs());

        while (running) {
            try (ServerSocket server = new ServerSocket()) {
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(port));
                serverSocket = server;
                log.info("Python写回OPC端口已启动: {}", port);

                while (running) {
                    Socket socket = server.accept();
                    log.info("Python写回OPC客户端已连接: {}", socket.getRemoteSocketAddress());
                    clientExecutor.submit(() -> handleClient(socket));
                }
            } catch (SocketException e) {
                if (running) {
                    log.warn("Python写回OPC端口异常，将重试: port={}, error={}", port, e.getMessage());
                }
            } catch (Exception e) {
                if (running) {
                    log.warn("Python写回OPC端口启动失败，将重试: port={}, error={}", port, e.getMessage(), e);
                }
            } finally {
                serverSocket = null;
            }

            sleepQuietly(retryDelay);
        }
    }

    private void handleClient(Socket socket) {
        try (
                Socket ignored = socket;
                BufferedReader in = new BufferedReader(
                        new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                        true)
        ) {
            String line;
            while (running && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                out.println(handleLine(line));
            }
        } catch (Exception e) {
            log.info("Python写回OPC客户端已断开: {}", e.getMessage());
        }
    }

    String handleLine(String line) {
        try {
            OpcWriteCommand command = OpcWriteCommand.parse(line);
            boolean success = opcClient.write(command.getTag(), command.getValue());
            return success ? "OK" : "FAIL:WRITE_FALSE";
        } catch (OpcWriteCommand.BadFormatException e) {
            return "FAIL:BAD_FORMAT";
        } catch (Exception e) {
            log.warn("Python写回OPC失败: line={}, error={}", line, e.getMessage());
            return "FAIL:" + e.getClass().getSimpleName();
        }
    }

    @PreDestroy
    public synchronized void stop() {
        running = false;
        closeQuietly(serverSocket);
        shutdown(serverExecutor);
        shutdown(clientExecutor);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void shutdown(ExecutorService executor) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static void closeQuietly(ServerSocket server) {
        if (server == null) {
            return;
        }
        try {
            server.close();
        } catch (Exception ignored) {
        }
    }
}
