package com.example.hitmodel.service;

import com.example.hitmodel.config.OpcConfig;
import com.example.hitmodel.opc.da.OPCDAReadClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RealtimeOpcBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeOpcBroadcastService.class);

    private final OpcConfig opcConfig;
    private final OpcTagRepository tagRepository;
    private final RealtimeOpcMessageFormatter messageFormatter;
    private final CopyOnWriteArrayList<ClientConnection> clients = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile OPCDAReadClient readClient;
    private ExecutorService acceptExecutor;
    private ExecutorService collectExecutor;

    public RealtimeOpcBroadcastService(
            OpcConfig opcConfig,
            OpcTagRepository tagRepository,
            RealtimeOpcMessageFormatter messageFormatter
    ) {
        this.opcConfig = opcConfig;
        this.tagRepository = tagRepository;
        this.messageFormatter = messageFormatter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!opcConfig.getRealtime().isEnabled()) {
            log.info("OPC实时采集广播服务已关闭");
            return;
        }
        if (running) {
            return;
        }

        running = true;
        acceptExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("opc-realtime-accept"));
        collectExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("opc-realtime-collect"));
        acceptExecutor.submit(this::runAcceptLoop);
        collectExecutor.submit(this::runCollectLoop);
        log.info(
                "OPC实时采集广播服务正在启动: port={}, intervalMs={}",
                opcConfig.getRealtime().getBroadcastPort(),
                opcConfig.getRealtime().getCollectIntervalMs()
        );
    }

    private void runAcceptLoop() {
        int port = opcConfig.getRealtime().getBroadcastPort();
        long retryDelay = retryDelayMs();

        while (running) {
            try (ServerSocket server = new ServerSocket()) {
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(port));
                serverSocket = server;
                log.info("OPC实时采集广播端口已启动: {}", port);

                while (running) {
                    Socket socket = server.accept();
                    addClient(socket);
                }
            } catch (SocketException e) {
                if (running) {
                    log.warn("OPC实时采集广播端口异常，将重试: port={}, error={}", port, e.getMessage());
                }
            } catch (Exception e) {
                if (running) {
                    log.warn("OPC实时采集广播端口启动失败，将重试: port={}, error={}", port, e.getMessage(), e);
                }
            } finally {
                serverSocket = null;
            }

            sleepQuietly(retryDelay);
        }
    }

    private void runCollectLoop() {
        long retryDelay = retryDelayMs();

        while (running) {
            OPCDAReadClient client = null;
            try {
                List<String> tags = tagRepository.loadTags();
                if (tags.isEmpty()) {
                    throw new IllegalStateException("数据库未查询到可采集的OPC点位");
                }

                client = new OPCDAReadClient(
                        opcConfig.getHost(),
                        opcConfig.getDomain(),
                        opcConfig.getUser(),
                        opcConfig.getPassword(),
                        opcConfig.getClsid()
                );
                client.connect(tags);
                readClient = client;
                log.info("OPC实时采集循环已启动，点位数: {}", tags.size());

                while (running) {
                    Map<String, Object> values = client.readAll();
                    String message = messageFormatter.format(tags, values, LocalDateTime.now());
                    broadcast(message);
                    log.debug("OPC实时采集广播数据: {}", message);
                    sleepQuietly(Math.max(100, opcConfig.getRealtime().getCollectIntervalMs()));
                }
            } catch (Exception e) {
                if (running) {
                    log.warn("OPC实时采集循环异常，将在{}ms后重试: {}", retryDelay, e.getMessage(), e);
                }
            } finally {
                if (readClient == client) {
                    readClient = null;
                }
                if (client != null) {
                    client.close();
                }
            }

            sleepQuietly(retryDelay);
        }
    }

    private void addClient(Socket socket) {
        try {
            ClientConnection client = new ClientConnection(socket);
            clients.add(client);
            log.info("OPC实时采集广播客户端已连接: {}", socket.getRemoteSocketAddress());
        } catch (Exception e) {
            closeQuietly(socket);
            log.warn("OPC实时采集广播客户端接入失败: {}", e.getMessage());
        }
    }

    private void broadcast(String message) {
        for (ClientConnection client : clients) {
            if (!client.send(message)) {
                clients.remove(client);
                client.close();
            }
        }
    }

    @PreDestroy
    public synchronized void stop() {
        running = false;
        closeQuietly(serverSocket);
        closeQuietly(readClient);
        for (ClientConnection client : clients) {
            client.close();
        }
        clients.clear();
        shutdown(acceptExecutor);
        shutdown(collectExecutor);
    }

    private long retryDelayMs() {
        return Math.max(1000, opcConfig.getRealtime().getRetryDelayMs());
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

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
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

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    private static class ClientConnection {
        private final Socket socket;
        private final PrintWriter out;

        private ClientConnection(Socket socket) throws Exception {
            this.socket = socket;
            this.out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                    true
            );
        }

        private boolean send(String message) {
            out.println(message);
            return !out.checkError();
        }

        private void close() {
            out.close();
            closeQuietly(socket);
        }
    }
}
