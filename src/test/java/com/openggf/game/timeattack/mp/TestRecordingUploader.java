package com.openggf.game.timeattack.mp;

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
                        "/recordings/ab", "wss://m.example:27900/master"));
        assertEquals("http://127.0.0.1:1234/recordings/ab",
                RecordingUploader.resolveUploadUrl(
                        "/recordings/ab", "ws://127.0.0.1:1234/master"));
        assertEquals("https://m.example/recordings/ab",
                RecordingUploader.resolveUploadUrl(
                        "https://m.example/recordings/ab", "wss://m.example/master"));
        assertThrows(IllegalArgumentException.class, () ->
                RecordingUploader.resolveUploadUrl(
                        "https://cdn.example/recordings/ab", "wss://m.example/master"));
        assertThrows(IllegalArgumentException.class, () ->
                RecordingUploader.resolveUploadUrl(
                        "http://127.0.0.1/recordings/ab", "wss://m.example/master"));
        assertThrows(IllegalArgumentException.class, () ->
                RecordingUploader.resolveUploadUrl("/recordings/ab", ""));
        assertThrows(IllegalArgumentException.class, () ->
                RecordingUploader.resolveUploadUrl("/admin", "wss://m.example/master"));
    }
}
