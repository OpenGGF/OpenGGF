package com.openggf.net.master;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.VerdictCodec;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMasterHttpRoutes {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path dir;
    private SqliteIdentityStore store;
    private MasterConfig config;
    private VerifierRegistry verifiers;
    private VerificationJobQueue jobs;
    private EmbeddedChannel channel;
    private long now = 100;

    @BeforeEach
    void setUp() throws Exception {
        Path yaml = dir.resolve("master.yaml");
        Files.writeString(yaml, """
                plaintextForTest: true
                verifierRegistrationToken: register-me
                maxRecordingBytes: 8
                maxRecordingStorageBytes: 4
                recordingUploadMinIntervalMillis: 10
                """);
        config = MasterConfig.load(yaml);
        store = new SqliteIdentityStore(dir.resolve("ids.db"));
        TrustLadder ladder = new TrustLadder(store,
                new NewIdentityCache(100, 10_000, () -> now),
                config.thresholds(), () -> now);
        verifiers = new VerifierRegistry(() -> now, 1000);
        jobs = new VerificationJobQueue(() -> now, 1000);
        channel = new EmbeddedChannel(new MasterHttpRoutes(config,
                "session"::equals, new RecordingBlobStore(dir.resolve("recordings")),
                verifiers, jobs, new VerdictConsequences(store, ladder,
                () -> now, 0), () -> now, Runnable::run,
                (job, pass) -> { }));
    }

    @AfterEach
    void tearDown() {
        if (channel != null) channel.finishAndReleaseAll();
        if (store != null) store.close();
    }

    @Test
    void uploadChecksTokenHashAndSize() throws Exception {
        byte[] body = {1, 2, 3};
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body));
        assertEquals(HttpResponseStatus.UNAUTHORIZED,
                send(HttpMethod.PUT, "/recordings/" + hash, body, null, null).status());
        assertEquals(HttpResponseStatus.BAD_REQUEST,
                send(HttpMethod.PUT, "/recordings/" + "0".repeat(64), body,
                        "Bearer session", null).status());
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE,
                send(HttpMethod.PUT, "/recordings/" + hash, new byte[9],
                        "Bearer session", null).status());
        assertEquals(HttpResponseStatus.CONFLICT,
                send(HttpMethod.PUT, "/recordings/" + hash, body,
                        "Bearer session", null).status());
        jobs.submit(job("0.6:cafe", hash), 1000);
        assertEquals(HttpResponseStatus.NO_CONTENT,
                send(HttpMethod.PUT, "/recordings/" + hash, body,
                        "Bearer session", null).status());
    }

    @Test
    void uploadsAreRateAndTotalStorageBounded() throws Exception {
        byte[] first = {1, 2};
        String firstHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(first));
        jobs.submit(job("fp", firstHash), 1000);
        assertEquals(HttpResponseStatus.NO_CONTENT,
                send(HttpMethod.PUT, "/recordings/" + firstHash, first,
                        "Bearer session", null).status());

        byte[] second = {3, 4};
        String secondHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(second));
        jobs.submit(job("fp", secondHash), 1000);
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS,
                send(HttpMethod.PUT, "/recordings/" + secondHash, second,
                        "Bearer session", null).status());
        now += 11;
        assertEquals(HttpResponseStatus.NO_CONTENT,
                send(HttpMethod.PUT, "/recordings/" + secondHash, second,
                        "Bearer session", null).status());

        byte[] third = {5};
        String thirdHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(third));
        jobs.submit(job("fp", thirdHash), 1000);
        now += 11;
        assertEquals(HttpResponseStatus.INSUFFICIENT_STORAGE,
                send(HttpMethod.PUT, "/recordings/" + thirdHash, third,
                        "Bearer session", null).status());
    }

    @Test
    void registerLeaseAndSignedVerdict() throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("worker"));
        WorkerAuth worker = register(identity, Set.of("0.6:cafe"));
        VerificationJobQueue.Job candidate = job("0.6:cafe", "aa");
        String jobId = jobs.submit(candidate, 1000);
        jobs.onRecordingUploaded("aa", "player");

        FullHttpResponse leased = send(HttpMethod.GET, "/verifier/jobs", new byte[0],
                "Bearer " + worker.token(), worker.id());
        assertEquals(HttpResponseStatus.OK, leased.status());
        leased.release();
        byte[] signature = identity.sign(VerdictCodec.canonicalBytes(
                jobId, "r#1#2", "aa", VerdictCodec.RESULT_PASS));
        byte[] verdict = JSON.writeValueAsBytes(java.util.Map.of(
                "jobId", jobId, "result", VerdictCodec.RESULT_PASS,
                "signatureBase64", Base64.getEncoder().encodeToString(signature)));
        assertEquals(HttpResponseStatus.NO_CONTENT,
                send(HttpMethod.POST, "/verifier/verdicts", verdict,
                        "Bearer " + worker.token(), worker.id()).status());
        assertEquals(1, store.verdictsFor("player").size());
    }

    @Test
    void workerCannotPostMasterOnlyOrUnknownResult() throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("worker"));
        WorkerAuth worker = register(identity, Set.of("0.6:cafe"));
        for (String result : new String[]{VerdictCodec.RESULT_VOID_NO_UPLOAD, "BOGUS"}) {
            String jobId = jobs.submit(job("0.6:cafe", result), 1000);
            jobs.onRecordingUploaded(result, "player");
            FullHttpResponse leased = send(HttpMethod.GET, "/verifier/jobs", new byte[0],
                    "Bearer " + worker.token(), worker.id());
            assertEquals(HttpResponseStatus.OK, leased.status());
            leased.release();
            byte[] signature = identity.sign(VerdictCodec.canonicalBytes(
                    jobId, "r#1#2", result, result));
            byte[] verdict = JSON.writeValueAsBytes(java.util.Map.of(
                    "jobId", jobId, "result", result,
                    "signatureBase64", Base64.getEncoder().encodeToString(signature)));
            assertEquals(HttpResponseStatus.BAD_REQUEST,
                    send(HttpMethod.POST, "/verifier/verdicts", verdict,
                            "Bearer " + worker.token(), worker.id()).status());
            assertEquals(VerificationJobQueue.State.LEASED, jobs.stateOf(jobId));
        }
        assertEquals(0, store.verdictsFor("player").size());
    }

    @Test
    void badSignatureAndUnleasedOrForeignCompletionAreRejected() throws Exception {
        PlayerIdentity firstIdentity = PlayerIdentity.loadOrCreate(dir.resolve("worker-a"));
        PlayerIdentity secondIdentity = PlayerIdentity.loadOrCreate(dir.resolve("worker-b"));
        WorkerAuth first = register(firstIdentity, Set.of("0.6:cafe"));
        WorkerAuth second = register(secondIdentity, Set.of("0.6:cafe"));

        String unleasedId = jobs.submit(job("0.6:cafe", "u1"), 1000);
        jobs.onRecordingUploaded("u1", "player");
        assertEquals(HttpResponseStatus.BAD_REQUEST,
                postVerdict(first, unleasedId, "u1", VerdictCodec.RESULT_PASS,
                        new byte[]{1, 2, 3}).status());
        assertEquals(VerificationJobQueue.State.QUEUED, jobs.stateOf(unleasedId));
        byte[] unleasedSignature = firstIdentity.sign(VerdictCodec.canonicalBytes(
                unleasedId, "r#1#2", "u1", VerdictCodec.RESULT_PASS));
        assertEquals(HttpResponseStatus.CONFLICT,
                postVerdict(first, unleasedId, "u1", VerdictCodec.RESULT_PASS,
                        unleasedSignature).status());
        assertEquals(VerificationJobQueue.State.QUEUED, jobs.stateOf(unleasedId));

        FullHttpResponse lease = send(HttpMethod.GET, "/verifier/jobs", new byte[0],
                "Bearer " + first.token(), first.id());
        assertEquals(HttpResponseStatus.OK, lease.status());
        lease.release();
        byte[] foreignSignature = secondIdentity.sign(VerdictCodec.canonicalBytes(
                unleasedId, "r#1#2", "u1", VerdictCodec.RESULT_PASS));
        assertEquals(HttpResponseStatus.CONFLICT,
                postVerdict(second, unleasedId, "u1", VerdictCodec.RESULT_PASS,
                        foreignSignature).status());
        assertEquals(VerificationJobQueue.State.LEASED, jobs.stateOf(unleasedId));

        byte[] ownerSignature = firstIdentity.sign(VerdictCodec.canonicalBytes(
                unleasedId, "r#1#2", "u1", VerdictCodec.RESULT_PASS));
        assertEquals(HttpResponseStatus.NO_CONTENT,
                postVerdict(first, unleasedId, "u1", VerdictCodec.RESULT_PASS,
                        ownerSignature).status());
    }

    @Test
    void randomHttpRoutePermutationsStayBoundedAndKeepChannelAlive() {
        Random random = new Random(0x51A7_E5L);
        HttpMethod[] methods = {
                HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                HttpMethod.DELETE, HttpMethod.PATCH
        };
        String[] fixedPaths = {
                "/verifier/register", "/verifier/jobs", "/verifier/verdicts",
                "/recordings/" + "0".repeat(64)
        };
        String[] authorizations = {
                null, "Bearer session", "Bearer register-me", "Bearer invalid",
                "Basic Zm9vOmJhcg=="
        };
        for (int iteration = 0; iteration < 5_000; iteration++) {
            HttpMethod method = methods[random.nextInt(methods.length)];
            String path = random.nextBoolean()
                    ? fixedPaths[random.nextInt(fixedPaths.length)]
                    : (random.nextBoolean() ? "/verifier/" : "/recordings/")
                    + randomAscii(random, random.nextInt(96));
            byte[] body = new byte[random.nextInt(129)];
            random.nextBytes(body);
            String authorization = authorizations[random.nextInt(authorizations.length)];
            String workerId = random.nextBoolean()
                    ? null : randomAscii(random, random.nextInt(65));

            FullHttpResponse response = send(method, path, body, authorization, workerId);
            assertTrue(response.status().code() >= 200 && response.status().code() < 600);
            response.release();
            assertTrue(channel.isOpen(), "route closed channel at iteration " + iteration);
        }
    }

    private WorkerAuth register(PlayerIdentity identity, Set<String> fingerprints)
            throws Exception {
        byte[] body = JSON.writeValueAsBytes(java.util.Map.of(
                "pubKeyBase64", Base64.getEncoder().encodeToString(
                        identity.publicKeyEncoded()),
                "fingerprints", fingerprints));
        FullHttpResponse response = send(HttpMethod.POST, "/verifier/register", body,
                "Bearer register-me", null);
        assertEquals(HttpResponseStatus.OK, response.status());
        JsonNode json = JSON.readTree(bytes(response));
        response.release();
        return new WorkerAuth(json.get("workerId").asText(),
                json.get("workerToken").asText());
    }

    private FullHttpResponse postVerdict(WorkerAuth worker, String jobId,
                                         String hash, String result, byte[] signature)
            throws Exception {
        byte[] body = JSON.writeValueAsBytes(java.util.Map.of(
                "jobId", jobId, "result", result,
                "signatureBase64", Base64.getEncoder().encodeToString(signature)));
        return send(HttpMethod.POST, "/verifier/verdicts", body,
                "Bearer " + worker.token(), worker.id());
    }

    private FullHttpResponse send(HttpMethod method, String path, byte[] body,
                                  String authorization, String workerId) {
        var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, path,
                Unpooled.wrappedBuffer(body));
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        if (authorization != null) request.headers().set(
                HttpHeaderNames.AUTHORIZATION, authorization);
        if (workerId != null) request.headers().set("X-Worker-Id", workerId);
        channel.writeInbound(request);
        channel.runPendingTasks();
        FullHttpResponse response = channel.readOutbound();
        assertNotNull(response);
        return response;
    }

    private static byte[] bytes(FullHttpResponse response) {
        byte[] bytes = new byte[response.content().readableBytes()];
        response.content().getBytes(response.content().readerIndex(), bytes);
        return bytes;
    }

    private static String randomAscii(Random random, int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            value.append((char) ('!' + random.nextInt('~' - '!' + 1)));
        }
        return value.toString();
    }

    private static VerificationJobQueue.Job job(String fingerprint, String hash) {
        return new VerificationJobQueue.Job(null, "r", 1, "player", "r#1#2",
                fingerprint, "s3k:0:0", "sonic", 100, 1, 101,
                hash, "bb", false, 0);
    }

    private record WorkerAuth(String id, String token) { }
}
