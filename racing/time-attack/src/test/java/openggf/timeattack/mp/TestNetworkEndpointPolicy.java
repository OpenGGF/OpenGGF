package openggf.timeattack.mp;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TestNetworkEndpointPolicy {
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
