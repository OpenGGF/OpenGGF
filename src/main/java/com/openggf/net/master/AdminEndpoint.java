package com.openggf.net.master;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Loopback-only operator endpoint for sanctions, attack mode, and audit access. */
final class AdminEndpoint implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MasterConfig config;
    private final MasterServer master;
    private final Path auditPath;
    private final HttpServer server;
    private final ExecutorService executor;

    private AdminEndpoint(MasterConfig config, MasterServer master, Path dataDir,
                          HttpServer server, ExecutorService executor) {
        this.config = config;
        this.master = master;
        this.auditPath = dataDir.resolve("admin-audit.jsonl");
        this.server = server;
        this.executor = executor;
    }

    static AdminEndpoint start(MasterConfig config, MasterServer master, Path dataDir)
            throws IOException {
        if (config.adminToken() == null || config.adminToken().isBlank()) {
            throw new IllegalArgumentException("admin token is required");
        }
        HttpServer http = HttpServer.create(
                new InetSocketAddress("127.0.0.1", config.adminPort()), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AdminEndpoint endpoint = new AdminEndpoint(config, master, dataDir, http, executor);
        http.createContext("/admin/sanction", endpoint::sanction);
        http.createContext("/admin/attack-mode", endpoint::attackMode);
        http.createContext("/admin/audit", endpoint::audit);
        http.setExecutor(executor);
        http.start();
        return endpoint;
    }

    int port() {
        return server.getAddress().getPort();
    }

    private void sanction(HttpExchange exchange) throws IOException {
        if (!authorize(exchange) || !requireMethod(exchange, "POST")) {
            return;
        }
        try {
            Map<String, Object> body = body(exchange);
            String fingerprint = requiredString(body, "fingerprint");
            String type = requiredString(body, "type");
            String reason = requiredString(body, "reason");
            if (!("BAN".equals(type) || "TIMEOUT".equals(type))) {
                throw new IllegalArgumentException("type must be BAN or TIMEOUT");
            }
            long durationHours = number(body, "durationHours");
            long now = System.currentTimeMillis();
            long expiry = durationHours <= 0 ? Long.MAX_VALUE
                    : Math.addExact(now, Math.multiplyExact(durationHours, 3_600_000L));
            runBroker(() -> master.sanction(new IdentityStore.SanctionRecord(
                    fingerprint, type, reason, actor(), now, expiry)));
            appendAudit("sanction:" + type, reason, now);
            respond(exchange, 200, "ok");
        } catch (IllegalArgumentException | ArithmeticException e) {
            respond(exchange, 400, e.getMessage());
        } catch (RuntimeException e) {
            respond(exchange, 500, "admin action failed");
        }
    }

    private void attackMode(HttpExchange exchange) throws IOException {
        if (!authorize(exchange) || !requireMethod(exchange, "POST")) {
            return;
        }
        try {
            Map<String, Object> body = body(exchange);
            Object enabledValue = body.get("enabled");
            if (!(enabledValue instanceof Boolean enabled)) {
                throw new IllegalArgumentException("enabled must be boolean");
            }
            runBroker(() -> master.broker().setAttackMode(enabled));
            appendAudit("attack-mode", Boolean.toString(enabled), System.currentTimeMillis());
            respond(exchange, 200, "ok");
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, e.getMessage());
        } catch (RuntimeException e) {
            respond(exchange, 500, "admin action failed");
        }
    }

    private void audit(HttpExchange exchange) throws IOException {
        if (!authorize(exchange) || !requireMethod(exchange, "GET")) {
            return;
        }
        respond(exchange, 200, Files.exists(auditPath)
                ? Files.readString(auditPath) : "");
    }

    private boolean authorize(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!("Bearer " + config.adminToken()).equals(authorization)) {
            respond(exchange, 401, "unauthorized");
            return false;
        }
        return true;
    }

    private static boolean requireMethod(HttpExchange exchange, String method)
            throws IOException {
        if (!method.equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "method not allowed");
            return false;
        }
        return true;
    }

    private static Map<String, Object> body(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("request body too large");
        }
        try {
            return JSON.readValue(bytes, new TypeReference<>() { });
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("invalid JSON body", e);
        }
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return text;
    }

    private static long number(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be numeric");
        }
        return number.longValue();
    }

    private void runBroker(Runnable action) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        master.execute(() -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("broker action timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("broker action interrupted", e);
        }
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private synchronized void appendAudit(String action, String reason, long timestamp) {
        try {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("actor", actor());
            line.put("action", action);
            line.put("reason", reason);
            line.put("timestamp", timestamp);
            Files.writeString(auditPath, JSON.writeValueAsString(line)
                            + System.lineSeparator(), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("failed to append admin audit", e);
        }
    }

    private String actor() {
        String token = config.adminToken();
        return "token:..." + token.substring(Math.max(0, token.length() - 6));
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
