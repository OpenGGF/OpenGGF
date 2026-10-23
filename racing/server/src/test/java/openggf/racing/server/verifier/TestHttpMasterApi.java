package openggf.racing.server.verifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.VerdictCodec;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import openggf.racing.server.master.VerificationJobQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Status handling of the verifier's HTTP transport against a scripted loopback master. */
@Timeout(30)
class TestHttpMasterApi {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HASH = "0".repeat(64);

    private record Reply(int status, byte[] body) {
        static Reply empty(int status) {
            return new Reply(status, null);
        }

        static Reply json(String body) {
            return new Reply(200, body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private record Request(String path, String authorization) { }

    @TempDir Path dir;
    private final Map<String, ConcurrentLinkedDeque<Reply>> replies = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private HttpMasterApi api;

    @BeforeEach
    void startMaster() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::answer);
        server.start();
        api = new HttpMasterApi("http://127.0.0.1:" + server.getAddress().getPort(),
                "register-me", false);
        script("/verifier/register", registered("t1"));
    }

    @AfterEach
    void stopMaster() {
        if (server != null) server.stop(0);
    }

    @Test
    void conflictingVerdictIsReportedAsRefusedInsteadOfThrown() throws Exception {
        api.register(new byte[] {1}, Set.of("fp"));
        script("/verifier/verdicts", Reply.empty(409), Reply.empty(204));

        assertFalse(api.postVerdict("vj-1", VerdictCodec.RESULT_PASS, new byte[] {2}));
        assertTrue(api.postVerdict("vj-1", VerdictCodec.RESULT_PASS, new byte[] {2}));
    }

    @Test
    void otherVerdictStatusesStillFailTheCycle() throws Exception {
        api.register(new byte[] {1}, Set.of("fp"));
        script("/verifier/verdicts", Reply.empty(503));

        assertThrows(IllegalStateException.class,
                () -> api.postVerdict("vj-1", VerdictCodec.RESULT_PASS, new byte[] {2}));
    }

    @Test
    void workerCycleWhoseVerdictConflictsCountsAsProcessed() throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("worker"));
        api.register(identity.publicKeyEncoded(), Set.of("fp"));
        VerificationJobQueue.Job job = new VerificationJobQueue.Job("vj-1", "r", 1,
                "player", "r#1#1", "fp", "s3k:0:0", "sonic",
                100, 1, 101, HASH, "gg", false, 0);
        script("/verifier/jobs", Reply.json(JSON.writeValueAsString(job)));
        // Bytes that do not hash to the job's recording decide FAIL_DIVERGENT without replay.
        script("/recordings/" + HASH, new Reply(200, new byte[] {1, 2, 3}));
        script("/verifier/verdicts", Reply.empty(409));
        VerifierWorker worker = new VerifierWorker(api, (recording, fingerprint) -> {
            throw new AssertionError("a mismatched recording must not replay");
        }, identity);

        assertTrue(worker.pollOnce());
        assertEquals(1, requests.stream()
                .filter(request -> request.path().equals("/verifier/verdicts")).count());
    }

    @Test
    void rejectedWorkerCredentialsRegisterAgainBeforeTheNextPoll() throws Exception {
        api.register(new byte[] {1}, Set.of("fp"));
        // A restarted master no longer knows the worker token.
        script("/verifier/register", registered("t2"));
        script("/verifier/jobs", Reply.empty(401), Reply.empty(204));

        assertThrows(IllegalStateException.class, api::pollJobJson);
        assertEquals(Optional.empty(), api.pollJobJson());

        assertEquals(2, requests.stream()
                .filter(request -> request.path().equals("/verifier/register")).count());
        assertEquals("Bearer t2", requests.getLast().authorization());
        assertEquals("/verifier/jobs", requests.getLast().path());
    }

    private static Reply registered(String token) {
        return Reply.json("{\"workerId\":\"w1\",\"workerToken\":\"" + token + "\"}");
    }

    /** Replies in order; the last reply repeats. */
    private void script(String path, Reply... scripted) {
        replies.put(path, new ConcurrentLinkedDeque<>(List.of(scripted)));
    }

    private void answer(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(path,
                    exchange.getRequestHeaders().getFirst("Authorization")));
            ConcurrentLinkedDeque<Reply> queue = replies.get(path);
            Reply reply = queue == null ? Reply.empty(404)
                    : queue.size() > 1 ? queue.poll() : queue.peek();
            if (reply.body() == null) {
                exchange.sendResponseHeaders(reply.status(), -1);
            } else {
                exchange.sendResponseHeaders(reply.status(), reply.body().length);
                exchange.getResponseBody().write(reply.body());
            }
        }
    }
}
