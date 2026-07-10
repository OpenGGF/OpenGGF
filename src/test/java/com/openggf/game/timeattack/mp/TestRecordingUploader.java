package com.openggf.game.timeattack.mp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRecordingUploader {

    @Test
    void putsBodyWithBearerToken() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/recordings/", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try (RecordingUploader uploader = new RecordingUploader("tok123", true)) {
            CountDownLatch done = new CountDownLatch(1);
            boolean[] ok = new boolean[1];
            uploader.upload("http://127.0.0.1:" + server.getAddress().getPort()
                            + "/recordings/aa", new byte[]{1, 2, 3}, result -> {
                        ok[0] = result;
                        done.countDown();
                    });
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(ok[0]);
            assertEquals("Bearer tok123", auth.get());
            assertArrayEquals(new byte[]{1, 2, 3}, body.get());
        } finally {
            server.stop(0);
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
        assertEquals("https://cdn.example/recordings/ab",
                RecordingUploader.resolveUploadUrl(
                        "https://cdn.example/recordings/ab", "wss://m.example/master"));
    }
}
