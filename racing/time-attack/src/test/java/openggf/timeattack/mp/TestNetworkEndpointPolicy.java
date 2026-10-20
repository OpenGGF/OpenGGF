package openggf.timeattack.mp;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TestNetworkEndpointPolicy {
    @Test
    void fakeTransportExplicitlySelectsNormalOrLocalInsecureAdmission() throws Exception {
        var delivered = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var completed = new java.util.concurrent.CountDownLatch(3);
        RecordingUploader.UploadTransport transport = (request, body) -> {
            delivered.add(request.uri().toString()); return 204;
        };
        try (var normal = new RecordingUploader("secret", transport);
             var insecure = new RecordingUploader("secret", transport, true)) {
            assertEquals(RecordingUploader.Admission.ACCEPTED, normal.upload(
                    "https://remote.invalid/recordings/normal", new byte[]{1}, ignored -> completed.countDown()));
            assertEquals(RecordingUploader.Admission.INVALID_URL, insecure.upload(
                    "https://remote.invalid/recordings/insecure", new byte[]{1}, ignored -> fail("rejected callback")));
            for (String endpoint : new String[]{"https://127.0.0.1/recordings/local4", "https://[::1]/recordings/local6"})
                assertEquals(RecordingUploader.Admission.ACCEPTED,
                        insecure.upload(endpoint, new byte[]{1}, ignored -> completed.countDown()));
            assertTrue(completed.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(java.util.Set.of("https://remote.invalid/recordings/normal",
                    "https://127.0.0.1/recordings/local4", "https://[::1]/recordings/local6"),
                    java.util.Set.copyOf(delivered));
        }
    }

    @Test
    void insecureUploaderRejectsRemoteHttpsBeforeTakingCustody() {
        try (var uploader = new RecordingUploader("secret", true)) {
            for (String endpoint : new String[]{"https://remote.invalid/recordings/a",
                    "https://localhost/recordings/a", "https://127.1/recordings/a"}) {
                assertEquals(RecordingUploader.Admission.INVALID_URL,
                        uploader.upload(endpoint, new byte[1024], ignored -> fail("rejected work has no callback")), endpoint);
                assertEquals(0, uploader.pendingCount());
                assertEquals(0, uploader.pendingBytes());
            }
        }
    }

    @Test
    void insecureUploaderAllowsLiteralLoopbackHttps() {
        try (var uploader = new RecordingUploader("secret", true)) {
            for (String endpoint : new String[]{"https://127.0.0.1:1/recordings/a",
                    "https://[::1]:1/recordings/a"}) {
                assertEquals(RecordingUploader.Admission.ACCEPTED,
                        uploader.upload(endpoint, new byte[]{1}, ignored -> {}), endpoint);
            }
        }
    }

    @Test
    void remotePlaintextMasterCannotAuthorizeAnUpload() {
        for (String master : new String[]{"ws://example.com/master", "ws://localhost/master",
                "ws://127.1/master", "wss://user@host.example/master", "wss://host.example/master#fragment"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> RecordingUploader.resolveUploadUrl("/recordings/a", master), master);
        }
    }

    @Test
    void directUploaderRejectsPlaintextAndConfusingAuthoritiesBeforeDispatch() {
        var sends = new AtomicInteger();
        try (var uploader = new RecordingUploader("secret", (request, body) -> {
            sends.incrementAndGet(); return 204;
        })) {
            for (String url : new String[]{"http://example.com/recordings/a", "http://localhost/recordings/a",
                    "https://user@example.com/recordings/a", "https://example.com/recordings/a#fragment"}) {
                assertEquals(RecordingUploader.Admission.INVALID_URL,
                        uploader.upload(url, new byte[]{1}, ignored -> {}), url);
            }
            assertEquals(0, sends.get());
        }
    }

    @Test
    void literalLoopbackAndTlsUploadOriginsRemainUsable() {
        assertEquals("http://127.0.0.1:8080/recordings/a", RecordingUploader.resolveUploadUrl(
                "/recordings/a", "ws://127.0.0.1:8080/master"));
        assertEquals("https://example.com/recordings/a", RecordingUploader.resolveUploadUrl(
                "/recordings/a", "wss://example.com/master"));
        assertEquals("http://[::1]:8080/recordings/a", RecordingUploader.resolveUploadUrl(
                "/recordings/a", "ws://[::1]:8080/master"));
        for (String host : new String[]{"localhost", "127.1", "0177.0.0.1", "127.0.0.1.example.com", "[::1%25eth0]"})
            assertFalse(NetworkEndpoints.literalLoopback(host), host);
    }
}
