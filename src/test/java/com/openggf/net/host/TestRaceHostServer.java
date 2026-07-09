package com.openggf.net.host;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class TestRaceHostServer {
    private static final String FP = "0.6:cafe1234";
    private RaceHostServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private static final class Probe implements WebSocket.Listener {
        final BlockingQueue<String> texts = new LinkedBlockingQueue<>();
        final StringBuilder partial = new StringBuilder();
        volatile boolean closed;

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                texts.add(partial.toString());
                partial.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closed = true;
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            closed = true;
        }
    }

    private WebSocket connect(Probe probe) throws Exception {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/race"), probe)
                .get(10, TimeUnit.SECONDS);
    }

    private static ControlMessage awaitMessage(Probe probe, Class<?> type) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            String text = probe.texts.poll(250, TimeUnit.MILLISECONDS);
            if (text != null) {
                ControlMessage message = ControlCodec.decode(text).message();
                if (type.isInstance(message)) {
                    return message;
                }
            }
        }
        throw new AssertionError("timed out waiting for " + type.getSimpleName());
    }

    @Test
    void fullHandshakeOverRealSocketAdmits(@TempDir Path dir) throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        server = start(host);
        assertTrue(server.port() > 0);
        PlayerIdentity client = PlayerIdentity.loadOrCreate(dir.resolve("client"));
        ClientHandshake handshake = new ClientHandshake(client, "Probe", FP);
        Probe probe = new Probe();
        WebSocket socket = connect(probe);
        socket.sendText(ControlCodec.encode(null, handshake.hello()), true).join();
        ControlMessage.Welcome welcome = (ControlMessage.Welcome)
                awaitMessage(probe, ControlMessage.Welcome.class);
        socket.sendText(ControlCodec.encode(null, handshake.onWelcome(welcome)), true).join();
        ControlMessage.JoinAccepted accepted = (ControlMessage.JoinAccepted)
                awaitMessage(probe, ControlMessage.JoinAccepted.class);
        assertEquals(0, accepted.playerSlot());
        assertFalse(accepted.room().verified());
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void garbageTextClosesConnection(@TempDir Path dir) throws Exception {
        server = start(PlayerIdentity.loadOrCreate(dir.resolve("host")));
        Probe probe = new Probe();
        WebSocket socket = connect(probe);
        socket.sendText("not json", true).join();
        awaitClosed(probe);
    }

    @Test
    void oversizedFragmentedTextClosesConnection(@TempDir Path dir) throws Exception {
        server = start(PlayerIdentity.loadOrCreate(dir.resolve("host")));
        Probe probe = new Probe();
        WebSocket socket = connect(probe);
        String fragment = "x".repeat(6000);
        socket.sendText(fragment, false).join();
        socket.sendText(fragment, true).join();
        awaitClosed(probe);
    }

    @Test
    void hostCanStartRoundViaExecute(@TempDir Path dir) throws Exception {
        server = start(PlayerIdentity.loadOrCreate(dir.resolve("host")));
        PlayerIdentity client = PlayerIdentity.loadOrCreate(dir.resolve("client"));
        ClientHandshake handshake = new ClientHandshake(client, "Probe", FP);
        Probe probe = new Probe();
        WebSocket socket = connect(probe);
        socket.sendText(ControlCodec.encode(null, handshake.hello()), true).join();
        ControlMessage.Welcome welcome = (ControlMessage.Welcome)
                awaitMessage(probe, ControlMessage.Welcome.class);
        socket.sendText(ControlCodec.encode(null, handshake.onWelcome(welcome)), true).join();
        awaitMessage(probe, ControlMessage.JoinAccepted.class);
        server.execute(() -> server.room().requestStartRound(
                new ControlMessage.RoundConfig("s3k", 0, 0, 60, "OPEN", null)));
        ControlMessage.RoundStart start = (ControlMessage.RoundStart)
                awaitMessage(probe, ControlMessage.RoundStart.class);
        assertTrue(start.deadlineHubMillis() > start.countdownEndsAtHubMillis());
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    private static RaceHostServer start(PlayerIdentity host) {
        return RaceHostServer.start(0,
                new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 8, FP),
                host, TrackValidationProfileSource.none());
    }

    private static void awaitClosed(Probe probe) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!probe.closed && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(probe.closed);
    }
}
