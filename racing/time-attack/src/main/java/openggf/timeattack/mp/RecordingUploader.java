package openggf.timeattack.mp;

import openggf.timeattack.AttemptInputRecording;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;

/** Asynchronous out-of-band uploader for input-only attempt recordings. */
public final class RecordingUploader implements AutoCloseable {
    private final Logger LOGGER = Logger.getLogger(RecordingUploader.class.getName());
    private final Duration requestTimeout = Duration.ofSeconds(10);
    private static final long RETRY_DELAY_MILLIS = 2_000;
    static final int MAX_PENDING = 8;
    static final int MAX_PENDING_BYTES = MAX_PENDING * AttemptInputRecording.MAX_ENCODED_BYTES;
    private static final int MAX_COMPLETED = 16;

    /** Only ACCEPTED owns a completion callback; duplicates share existing transport work. */
    public enum Admission { ACCEPTED, DUPLICATE, FULL, TOO_LARGE, CONFLICT, CLOSED, INVALID_URL }

    private final String sessionToken;
    private final ThreadPoolExecutor executor;
    private final UploadTransport transport;
    private final Map<String, Job> pending = new LinkedHashMap<>();
    private final Map<String, byte[]> completed = new LinkedHashMap<>();
    private int pendingBytes;
    private boolean closed;

    @FunctionalInterface
    interface UploadTransport {
        int send(HttpRequest request, byte[] body) throws Exception;
    }

    public RecordingUploader(String sessionToken, boolean trustInsecure) {
        this(sessionToken, httpTransport(trustInsecure));
    }

    private static UploadTransport httpTransport(boolean trustInsecure) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        if (trustInsecure) builder.sslContext(insecureSslContext());
        HttpClient client = builder.build();
        return new HttpTransport(client);
    }

    private record HttpTransport(HttpClient client) implements UploadTransport {
        @Override public int send(HttpRequest request, byte[] body) throws Exception {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    RecordingUploader(String sessionToken, UploadTransport transport) {
        this.sessionToken = Objects.requireNonNull(sessionToken, "sessionToken");
        this.transport = Objects.requireNonNull(transport, "transport");
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING), runnable -> {
                    Thread thread = new Thread(runnable, "recording-uploader");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** Bounded admission precedes the body copy. Each instance retains its original session token. */
    public synchronized Admission upload(String uploadUrl, byte[] recording, Consumer<Boolean> onDone) {
        Objects.requireNonNull(recording, "recording");
        Objects.requireNonNull(onDone, "onDone");
        if (closed) return Admission.CLOSED;
        if (recording.length > AttemptInputRecording.MAX_ENCODED_BYTES) return Admission.TOO_LARGE;
        if (uploadUrl == null || uploadUrl.length() > 2048) return Admission.INVALID_URL;
        Job existing = pending.get(uploadUrl);
        byte[] prior = existing == null ? completed.get(uploadUrl) : existing.digest;
        if (prior == null && (pending.size() >= MAX_PENDING || recording.length > MAX_PENDING_BYTES - pendingBytes)) {
            return Admission.FULL;
        }
        if (prior != null) return Arrays.equals(prior, digest(recording)) ? Admission.DUPLICATE : Admission.CONFLICT;
        byte[] body = recording.clone();
        Job job = new Job(uploadUrl, body, digest(body), onDone);
        pending.put(uploadUrl, job);
        pendingBytes += recording.length;
        executor.execute(job); // close and admission share this monitor: shutdown cannot race admission.
        return Admission.ACCEPTED;
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private synchronized boolean isClosed() {
        return closed;
    }

    synchronized int pendingCount() { return pending.size(); }
    synchronized int pendingBytes() { return pendingBytes; }

    private final class Job implements Runnable {
        private final String url;
        private final byte[] body;
        private final byte[] digest;
        private final Consumer<Boolean> callback;
        private final AtomicBoolean resolved = new AtomicBoolean();

        private Job(String url, byte[] body, byte[] digest, Consumer<Boolean> callback) {
            this.url = url;
            this.body = body;
            this.digest = digest;
            this.callback = callback;
        }

        @Override public void run() {
            boolean result = !isClosed() && send(url, body);
            if (!result && !isClosed() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(RETRY_DELAY_MILLIS);
                    result = !isClosed() && send(url, body);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            finish(result);
        }

        private void finish(boolean success) {
            boolean result;
            synchronized (RecordingUploader.this) {
                if (!resolved.compareAndSet(false, true)) return;
                result = success && !closed;
                pending.remove(url);
                pendingBytes -= body.length;
                if (result) {
                    completed.put(url, digest);
                    if (completed.size() > MAX_COMPLETED) completed.remove(completed.keySet().iterator().next());
                }
            }
            try {
                callback.accept(result);
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Recording completion callback failed", failure);
            }
        }
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
        ArrayList<Job> accepted;
        synchronized (this) {
            if (closed) return;
            closed = true;
            accepted = new ArrayList<>(pending.values());
            completed.clear();
            executor.shutdownNow();
        }
        // Includes work removed from the executor queue; a late worker result cannot resolve twice.
        for (Job job : accepted) job.finish(false);
        if (transport instanceof HttpTransport http) http.client().shutdownNow();
    }

    private boolean send(String uploadUrl, byte[] recording) {
        if (isClosed() || Thread.currentThread().isInterrupted()) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + sessionToken)
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(recording))
                    .build();
            if (isClosed() || Thread.currentThread().isInterrupted()) return false;
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
