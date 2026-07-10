package com.openggf.tools.verifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.timeattack.AttemptReplayHarness;
import com.openggf.net.identity.PlayerIdentity;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** CLI entry point for the operator-supplied-ROM replay verification worker. */
public final class VerifierMain {
    private VerifierMain() { }

    public static void main(String[] args) throws Exception {
        Arguments options = Arguments.parse(args);
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(options.dataDir());
        Map<String, Path> roms = new LinkedHashMap<>();
        for (Path rom : options.roms()) {
            roms.put(AttemptReplayHarness.fingerprintForRom(rom), rom);
        }
        HttpMasterApi api = new HttpMasterApi(options.master(),
                options.registrationToken(), options.trustInsecure());
        api.register(identity.publicKeyEncoded(), roms.keySet());
        VerifierWorker worker = new VerifierWorker(api, (recording, fingerprint) -> {
            Path rom = roms.get(fingerprint);
            return rom == null
                    ? new AttemptReplayHarness.Result(false, -1, -1, -1,
                    "", 0, "fingerprint mismatch")
                    : AttemptReplayHarness.replay(recording, rom);
        }, identity);
        do {
            boolean processed = worker.pollOnce();
            if (options.once()) {
                break;
            }
            if (!processed) {
                Thread.sleep(2_000);
            }
        } while (!Thread.currentThread().isInterrupted());
    }

    private record Arguments(String master, String registrationToken,
                             List<Path> roms, Path dataDir,
                             boolean trustInsecure, boolean once) {
        private static Arguments parse(String[] args) {
            String master = null;
            String registration = null;
            Path data = Path.of("verifier-data");
            List<Path> roms = new ArrayList<>();
            boolean insecure = false;
            boolean once = false;
            for (int index = 0; index < args.length; index++) {
                switch (args[index]) {
                    case "--master" -> master = value(args, ++index, "--master");
                    case "--registration-token" -> registration = value(
                            args, ++index, "--registration-token");
                    case "--rom" -> roms.add(Path.of(value(args, ++index, "--rom")));
                    case "--data" -> data = Path.of(value(args, ++index, "--data"));
                    case "--trust-insecure" -> insecure = true;
                    case "--once" -> once = true;
                    default -> throw new IllegalArgumentException(
                            "unknown argument " + args[index]);
                }
            }
            if (master == null || registration == null || roms.isEmpty()) {
                throw new IllegalArgumentException(
                        "required: --master URL --registration-token TOKEN --rom PATH");
            }
            return new Arguments(master.replaceAll("/+$", ""), registration,
                    List.copyOf(roms), data, insecure, once);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException("missing value for " + option);
            }
            return args[index];
        }
    }

    private static final class HttpMasterApi implements VerifierWorker.MasterApi {
        private static final ObjectMapper JSON = new ObjectMapper();
        private final String base;
        private final String registrationToken;
        private final HttpClient client;
        private String workerId;
        private String workerToken;

        private HttpMasterApi(String base, String registrationToken,
                              boolean trustInsecure) {
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
                requireStatus(response.statusCode(), 200);
                JsonNode json = JSON.readTree(response.body());
                workerId = json.get("workerId").asText();
                workerToken = json.get("workerToken").asText();
                return workerToken;
            } catch (Exception failure) {
                throw new IllegalStateException("verifier registration failed", failure);
            }
        }

        @Override
        public Optional<String> pollJobJson() {
            HttpResponse<String> response = send(workerRequest(base + "/verifier/jobs").GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) return Optional.empty();
            requireStatus(response.statusCode(), 200);
            return Optional.of(response.body());
        }

        @Override
        public byte[] fetchRecording(String hashHex) {
            HttpResponse<byte[]> response = send(workerRequest(
                            base + "/recordings/" + hashHex).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            requireStatus(response.statusCode(), 200);
            return response.body();
        }

        @Override
        public void postVerdict(String jobId, String result, byte[] signature) {
            try {
                byte[] body = JSON.writeValueAsBytes(Map.of(
                        "jobId", jobId, "result", result,
                        "signatureBase64", Base64.getEncoder().encodeToString(signature)));
                HttpResponse<Void> response = send(workerRequest(
                                base + "/verifier/verdicts")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                        HttpResponse.BodyHandlers.discarding());
                requireStatus(response.statusCode(), 204);
            } catch (Exception failure) {
                throw new IllegalStateException("verdict post failed", failure);
            }
        }

        private HttpRequest.Builder workerRequest(String url) {
            return HttpRequest.newBuilder(URI.create(url))
                    .header("X-Worker-Id", workerId)
                    .header("Authorization", "Bearer " + workerToken)
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

        private static void requireStatus(int actual, int expected) {
            if (actual != expected) {
                throw new IllegalStateException(
                        "unexpected verifier API status " + actual + ", expected " + expected);
            }
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
}
