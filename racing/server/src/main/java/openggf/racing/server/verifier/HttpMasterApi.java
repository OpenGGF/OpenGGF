package openggf.racing.server.verifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** HTTP(S) transport from {@link VerifierWorker} to the master's verifier routes. */
final class HttpMasterApi implements VerifierWorker.MasterApi {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int OK = 200;
    private static final int NO_CONTENT = 204;
    private static final int UNAUTHORIZED = 401;
    private static final int CONFLICT = 409;

    private record Credentials(String workerId, String workerToken) { }

    private record Registration(byte[] publicKeyEncoded, Set<String> fingerprints) { }

    private final String base;
    private final String registrationToken;
    private final HttpClient client;
    // The replay heartbeat thread reads credentials while the cycle thread may re-register.
    private volatile Credentials credentials;
    private volatile Registration registration;
    private volatile boolean credentialsRejected;

    HttpMasterApi(String base, String registrationToken, boolean trustInsecure) {
        this.base = base;
        this.registrationToken = registrationToken;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        if (trustInsecure) builder.sslContext(insecureSslContext());
        client = builder.build();
    }

    @Override
    public String register(byte[] publicKeyEncoded, Set<String> fingerprints) {
        try {
            byte[] body = JSON.writeValueAsBytes(Map.of(
                    "pubKeyBase64", Base64.getEncoder().encodeToString(publicKeyEncoded),
                    "fingerprints", fingerprints));
            HttpResponse<String> response = send(HttpRequest.newBuilder(
                            URI.create(base + "/verifier/register"))
                    .header("Authorization", "Bearer " + registrationToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            requireStatus(response.statusCode(), OK);
            JsonNode json = JSON.readTree(response.body());
            credentials = new Credentials(json.get("workerId").asText(),
                    json.get("workerToken").asText());
            registration = new Registration(publicKeyEncoded.clone(), Set.copyOf(fingerprints));
            credentialsRejected = false;
            return credentials.workerToken();
        } catch (Exception failure) {
            throw new IllegalStateException("verifier registration failed", failure);
        }
    }

    @Override
    public Optional<String> pollJobJson() {
        reRegisterIfRejected();
        HttpResponse<String> response = send(workerRequest(base + "/verifier/jobs").GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == NO_CONTENT) return Optional.empty();
        requireStatus(response.statusCode(), OK);
        return Optional.of(response.body());
    }

    @Override
    public void heartbeat() {
        HttpResponse<Void> response = send(workerRequest(base + "/verifier/heartbeat")
                .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
        requireStatus(response.statusCode(), NO_CONTENT);
    }

    @Override
    public byte[] fetchRecording(String hashHex) {
        HttpResponse<byte[]> response = send(workerRequest(
                        base + "/recordings/" + hashHex).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        requireStatus(response.statusCode(), OK);
        return response.body();
    }

    @Override
    public boolean postVerdict(String jobId, String result, byte[] signature) {
        try {
            byte[] body = JSON.writeValueAsBytes(Map.of(
                    "jobId", jobId, "result", result,
                    "signatureBase64", Base64.getEncoder().encodeToString(signature)));
            HttpResponse<Void> response = send(workerRequest(
                            base + "/verifier/verdicts")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == CONFLICT) {
                // MasterHttpRoutes answers 409 when the job is gone (voided or its attempt
                // superseded) or no longer leased to this worker (re-leased after expiry).
                return false;
            }
            requireStatus(response.statusCode(), NO_CONTENT);
            return true;
        } catch (Exception failure) {
            throw new IllegalStateException("verdict post failed", failure);
        }
    }

    private void reRegisterIfRejected() {
        Registration previous = registration;
        if (credentialsRejected && previous != null) {
            // A restarted master, or one that expired this worker as stale, answers 401
            // to every worker route until the worker registers again.
            register(previous.publicKeyEncoded(), previous.fingerprints());
        }
    }

    private HttpRequest.Builder workerRequest(String url) {
        Credentials current = credentials;
        if (current == null) {
            throw new IllegalStateException("verifier is not registered");
        }
        return HttpRequest.newBuilder(URI.create(url))
                .header("X-Worker-Id", current.workerId())
                .header("Authorization", "Bearer " + current.workerToken())
                .timeout(Duration.ofSeconds(30));
    }

    private <T> HttpResponse<T> send(HttpRequest request,
                                     HttpResponse.BodyHandler<T> handler) {
        try {
            return client.send(request, handler);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("verifier HTTP request failed", failure);
        }
    }

    private void requireStatus(int actual, int expected) {
        if (actual == expected) {
            return;
        }
        if (actual == UNAUTHORIZED) {
            credentialsRejected = true;
        }
        throw new IllegalStateException(
                "unexpected verifier API status " + actual + ", expected " + expected);
    }

    private static SSLContext insecureSslContext() {
        try {
            TrustManager[] managers = {new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain,
                                                          String authType) { }
                @Override public void checkServerTrusted(X509Certificate[] chain,
                                                          String authType) { }
                @Override public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, managers, new SecureRandom());
            return context;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
