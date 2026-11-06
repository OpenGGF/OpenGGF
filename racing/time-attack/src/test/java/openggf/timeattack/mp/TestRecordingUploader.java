package openggf.timeattack.mp;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRecordingUploader {
    @Test
    void duplicateBurstSharesOneAcceptedUploadAndDoesNotGrowTheWorkQueue() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), barrier = new CountDownLatch(1);
        var sends = new java.util.concurrent.atomic.AtomicInteger();
        try (RecordingUploader uploader = new RecordingUploader("session-a", (request, body) -> {
            if (sends.incrementAndGet() == 1) {
                started.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            }
            return 204;
        })) {
            uploader.upload("https://m.example/recordings/same", new byte[]{1}, ok -> {});
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 1000; i++) uploader.upload("https://m.example/recordings/same", new byte[]{1}, ok -> {});
            uploader.upload("https://m.example/recordings/barrier", new byte[]{2}, ok -> barrier.countDown());
            release.countDown();
            assertTrue(barrier.await(5, TimeUnit.SECONDS));
            assertEquals(2, sends.get(), "duplicate requests must share pending custody and transport work");
        } finally {
            release.countDown();
        }
    }

    @Test
    void closeResolvesAcceptedQueuedCallbacksAndRejectsLateWorkWithoutThrowing() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), callbacks = new CountDownLatch(2);
        var results = new java.util.concurrent.CopyOnWriteArrayList<Boolean>();
        RecordingUploader uploader = new RecordingUploader("session-a", (request, body) -> {
            started.countDown();
            release.await();
            return 204;
        });
        try {
            uploader.upload("https://m.example/recordings/a", new byte[]{1}, ok -> { results.add(ok); callbacks.countDown(); });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            uploader.upload("https://m.example/recordings/b", new byte[]{2}, ok -> { results.add(ok); callbacks.countDown(); });
            uploader.close();
            release.countDown();
            assertTrue(callbacks.await(5, TimeUnit.SECONDS), "close must resolve queued as well as active custody");
            assertEquals(java.util.List.of(false, false), results);
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> uploader.upload(
                    "https://m.example/recordings/late", new byte[]{3}, ok -> {}));
        } finally {
            uploader.close();
            release.countDown();
        }
    }

    @Test
    void uniqueBurstRejectsBeforeCopyAndRetainsOnlyEightSessionBoundBodies() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        var sent = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var completed = new java.util.concurrent.atomic.AtomicInteger();
        try (RecordingUploader uploader = new RecordingUploader("original-session", (request, body) -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            assertEquals("Bearer original-session", request.headers().firstValue("Authorization").orElseThrow());
            assertEquals(7, body[0], "accepted bodies must be independent of caller mutation");
            sent.add(request.uri().getPath());
            return 204;
        })) {
            byte[] body = new byte[openggf.timeattack.AttemptInputRecording.MAX_ENCODED_BYTES];
            body[0] = 7;
            assertEquals(RecordingUploader.Admission.ACCEPTED, uploader.upload(
                    "https://m.example/recordings/0", body, ok -> completed.incrementAndGet()));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 1; i < 8; i++) assertEquals(RecordingUploader.Admission.ACCEPTED,
                    uploader.upload("https://m.example/recordings/" + i, body, ok -> completed.incrementAndGet()));
            for (int i = 8; i < 1000; i++) assertEquals(RecordingUploader.Admission.FULL,
                    uploader.upload("https://m.example/recordings/" + i, body, ok -> { throw new AssertionError("rejected callback"); }));
            assertEquals(8, uploader.pendingCount());
            assertTrue(uploader.pendingBytes() <= 8 * body.length);
            assertEquals(RecordingUploader.Admission.TOO_LARGE, uploader.upload(
                    "https://m.example/recordings/large", new byte[body.length + 1], ok -> {}));
            assertEquals(RecordingUploader.Admission.CONFLICT, uploader.upload(
                    "https://m.example/recordings/0", new byte[]{2}, ok -> {}));
            body[0] = 99;
            uploader.close(); // queued jobs must never send after session ownership is closed.
            release.countDown();
            assertEquals(8, completed.get());
            assertTrue(sent.size() <= 1);
            assertEquals(0, uploader.pendingCount());
            assertEquals(0, uploader.pendingBytes());
            assertEquals(RecordingUploader.Admission.CLOSED, uploader.upload(
                    "https://m.example/recordings/late", new byte[]{1}, ok -> {}));
        } finally { release.countDown(); }
    }

    @Test
    void admittedBodySnapshotAndTokenSurviveCallerMutationAcrossRetry() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        var sends = new java.util.concurrent.atomic.AtomicInteger();
        try (RecordingUploader uploader = new RecordingUploader("original-session", (request, body) -> {
            int ordinal = sends.incrementAndGet();
            if (ordinal == 1) { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
            assertArrayEquals(new byte[]{7}, body);
            assertEquals("Bearer original-session", request.headers().firstValue("Authorization").orElseThrow());
            return ordinal == 1 ? 503 : 204;
        })) {
            byte[] caller = {7};
            assertEquals(RecordingUploader.Admission.ACCEPTED, uploader.upload(
                    "https://m.example/recordings/snapshot", caller, ok -> { assertTrue(ok); done.countDown(); }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            caller[0] = 99; release.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(2, sends.get());
        } finally { release.countDown(); }
    }

    @Test
    void successfulDuplicatesDoNotSendAgainAndCallbackFaultDoesNotStopTheWorker() throws Exception {
        var sends = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch first = new CountDownLatch(1), second = new CountDownLatch(1);
        try (RecordingUploader uploader = new RecordingUploader("token", (request, body) -> {
            sends.incrementAndGet(); return 204;
        })) {
            uploader.upload("https://m.example/recordings/first", new byte[]{1}, ok -> {
                first.countDown(); throw new IllegalStateException("callback failure");
            });
            assertTrue(first.await(5, TimeUnit.SECONDS));
            assertEquals(RecordingUploader.Admission.DUPLICATE, uploader.upload(
                    "https://m.example/recordings/first", new byte[]{1}, ok -> {}));
            uploader.upload("https://m.example/recordings/second", new byte[]{2}, ok -> second.countDown());
            assertTrue(second.await(5, TimeUnit.SECONDS));
            assertEquals(2, sends.get());
        }
    }

    @Test
    void putsBodyWithBearerToken() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        RecordingUploader.UploadTransport transport = (request, requestBody) -> {
            auth.set(request.headers().firstValue("Authorization").orElse(null));
            body.set(requestBody.clone());
            assertEquals(HttpRequest.BodyPublishers.ofByteArray(requestBody).contentLength(),
                    request.bodyPublisher().orElseThrow().contentLength());
            return 204;
        };
        try (RecordingUploader uploader = new RecordingUploader("tok123", transport)) {
            CountDownLatch done = new CountDownLatch(1);
            boolean[] ok = new boolean[1];
            uploader.upload("https://m.example/recordings/aa", new byte[]{1, 2, 3}, result -> {
                        ok[0] = result;
                        done.countDown();
                    });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(ok[0]);
            assertEquals("Bearer tok123", auth.get());
            assertArrayEquals(new byte[]{1, 2, 3}, body.get());
        }
    }

    @Test
    void resolvesPathOnlyUploadUrlsAgainstConfiguredMasterUrl() {
        assertEquals("https://m.example:27900/recordings/ab",
                RecordingUploader.resolveUploadUrl(
                        "/recordings/ab", java.net.URI.create("wss://m.example:27900/master")));
        assertEquals("http://127.0.0.1:1234/recordings/ab",
                RecordingUploader.resolveUploadUrl(
                        "/recordings/ab", java.net.URI.create("ws://127.0.0.1:1234/master")));
        assertEquals("https://m.example/recordings/ab",
                RecordingUploader.resolveUploadUrl(
                        "https://m.example/recordings/ab", java.net.URI.create("wss://m.example/master")));
        assertThrows(IllegalArgumentException.class, () ->
                RecordingUploader.resolveUploadUrl(
                        "https://cdn.example/recordings/ab", java.net.URI.create("wss://m.example/master")));
        assertThrows(IllegalArgumentException.class, () ->
                RecordingUploader.resolveUploadUrl(
                        "http://127.0.0.1/recordings/ab", java.net.URI.create("wss://m.example/master")));
        assertThrows(IllegalArgumentException.class, () ->
                RecordingUploader.resolveUploadUrl("/recordings/ab", null));
        assertThrows(IllegalArgumentException.class, () ->
                RecordingUploader.resolveUploadUrl("/admin", java.net.URI.create("wss://m.example/master")));
    }

    @Test
    void uploadsUseTheAdmittedEndpointsDiscoveredPort() {
        // The player typed a bare host; DNS supplied 27910, and the admitted endpoint carries it.
        java.net.URI admitted = java.net.URI.create("wss://multi-dev.openggf.com:27910/master");
        assertEquals("https://multi-dev.openggf.com:27910/recordings/ab",
                RecordingUploader.resolveUploadUrl("/recordings/ab", admitted));
        assertEquals("https://multi-dev.openggf.com:27910/recordings/ab",
                RecordingUploader.resolveUploadUrl("https://multi-dev.openggf.com:27910/recordings/ab", admitted));
        // Broken case: the settings-text origin (portless, so 443) is not the admitted origin.
        assertThrows(IllegalArgumentException.class, () -> RecordingUploader.resolveUploadUrl(
                "https://multi-dev.openggf.com/recordings/ab", admitted));
        assertThrows(IllegalArgumentException.class, () -> RecordingUploader.resolveUploadUrl(
                "https://multi-dev.openggf.com:27920/recordings/ab", admitted));
    }
}
