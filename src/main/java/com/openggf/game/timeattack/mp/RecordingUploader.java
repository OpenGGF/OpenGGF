package com.openggf.game.timeattack.mp;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Asynchronous out-of-band uploader for input-only attempt recordings. */
public final class RecordingUploader implements AutoCloseable {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long RETRY_DELAY_MILLIS = 2_000;

    private final String sessionToken;
    private final ExecutorService executor;
    private final UploadTransport transport;

    @FunctionalInterface
    interface UploadTransport {
        int send(HttpRequest request, byte[] body) throws Exception;
    }

    public RecordingUploader(String sessionToken, boolean trustInsecure) {
        this.sessionToken = Objects.requireNonNull(sessionToken, "sessionToken");
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "recording-uploader");
            thread.setDaemon(true);
            return thread;
        });
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT);
        if (trustInsecure) {
            builder.sslContext(insecureSslContext());
        }
        HttpClient client = builder.build();
        transport = (request, body) -> client.send(request,
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    RecordingUploader(String sessionToken, UploadTransport transport) {
        this.sessionToken = Objects.requireNonNull(sessionToken, "sessionToken");
        this.transport = Objects.requireNonNull(transport, "transport");
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "recording-uploader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void upload(String uploadUrl, byte[] recording, Consumer<Boolean> onDone) {
        byte[] body = recording.clone();
        executor.execute(() -> {
            boolean result = send(uploadUrl, body);
            if (!result && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(RETRY_DELAY_MILLIS);
                    result = send(uploadUrl, body);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            onDone.accept(result);
        });
    }

    public static String resolveUploadUrl(String uploadUrlOrPath,
                                          String configuredMasterUrl) {
        URI masterOrigin = masterHttpOrigin(configuredMasterUrl);
        URI requested = URI.create(Objects.requireNonNull(uploadUrlOrPath,
                "uploadUrlOrPath"));
        if (requested.getPath() == null
                || !requested.getPath().startsWith("/recordings/")) {
            throw new IllegalArgumentException("upload URL must target /recordings/");
        }
        if (requested.isAbsolute()) {
            if (!sameOrigin(requested, masterOrigin)) {
                throw new IllegalArgumentException(
                        "upload URL must match the configured master origin");
            }
            return requested.toString();
        }
        try {
            return new URI(masterOrigin.getScheme(), null, masterOrigin.getHost(),
                    masterOrigin.getPort(), requested.getPath(), requested.getQuery(), null)
                    .toString();
        } catch (java.net.URISyntaxException impossible) {
            throw new IllegalArgumentException("invalid upload URL", impossible);
        }
    }

    private static URI masterHttpOrigin(String configuredMasterUrl) {
        if (configuredMasterUrl == null || configuredMasterUrl.isBlank()) {
            throw new IllegalArgumentException("configured master URL is required");
        }
        URI master = URI.create(configuredMasterUrl);
        String scheme = switch (String.valueOf(master.getScheme()).toLowerCase()) {
            case "ws" -> "http";
            case "wss" -> "https";
            default -> throw new IllegalArgumentException(
                    "configured master URL must use WS(S)");
        };
        if (master.getHost() == null || master.getHost().isBlank()) {
            throw new IllegalArgumentException("configured master URL must include a host");
        }
        try {
            return new URI(scheme, null, master.getHost(), master.getPort(),
                    null, null, null);
        } catch (java.net.URISyntaxException impossible) {
            throw new IllegalArgumentException("invalid master URL", impossible);
        }
    }

    private static boolean sameOrigin(URI requested, URI master) {
        return requested.getScheme().equalsIgnoreCase(master.getScheme())
                && requested.getHost() != null
                && requested.getHost().equalsIgnoreCase(master.getHost())
                && effectivePort(requested) == effectivePort(master);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private boolean send(String uploadUrl, byte[] recording) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + sessionToken)
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(recording))
                    .build();
            int status = transport.send(request, recording);
            return status / 100 == 2;
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static SSLContext insecureSslContext() {
        try {
            TrustManager[] trustAll = {new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain,
                                                          String authType) { }
                @Override public void checkServerTrusted(X509Certificate[] chain,
                                                          String authType) { }
                @Override public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return context;
        } catch (Exception failure) {
            throw new IllegalStateException("unable to create insecure TLS context", failure);
        }
    }
}
