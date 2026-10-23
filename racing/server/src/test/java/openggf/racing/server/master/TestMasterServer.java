package openggf.racing.server.master;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import openggf.racing.protocol.ControlCodec;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
public class TestMasterServer {
    private MasterServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    public static MasterConfig testConfig() {
        MasterConfig defaults = MasterConfig.defaults();
        return new MasterConfig(0, null, null, true, "ids.db", 0,
                "secret-admin-token", defaults.establishedAgeHours(),
                defaults.establishedCleanRounds(), defaults.trustedAgeDays(),
                defaults.trustedCleanRounds(), 8, 8, false,
                defaults.maxRoomsPerIdentity(), defaults.maxRoomsPerIp(),
                defaults.roomHeartbeatTimeoutSeconds(), defaults.browserPageSize(),
                defaults.identityGcInactiveDays(), defaults.newIdentityCacheSize(),
                defaults.newIdentityCacheTtlMinutes());
    }

    static final class Probe implements WebSocket.Listener {
        final BlockingQueue<String> texts = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();

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
    }

    private ControlMessage await(Probe probe, Class<?> type) throws Exception {
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
    void healthIsAuthenticatedReadOnlyAndRejectsMutatingMethods(@TempDir Path dir)
            throws Exception {
        server = MasterServer.start(testConfig(), dir);
        URI health = URI.create("http://127.0.0.1:" + server.adminPort() + "/admin/health");
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertEquals(401, client.send(HttpRequest.newBuilder(health).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            HttpRequest.Builder authorized = HttpRequest.newBuilder(health)
                    .header("Authorization", "Bearer secret-admin-token");
            assertEquals(204, client.send(authorized.GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(405, client.send(authorized.POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
        }
        assertFalse(Files.exists(dir.resolve("admin-audit.jsonl")),
                "readiness must not add audit records on every probe");
        Path config = dir.resolve("health.yaml");
        Path token = dir.resolve("admin.secret");
        Files.writeString(config, "adminPort: " + server.adminPort()
                + "\nadminTokenFile: admin.secret\n");
        Files.writeString(token, "secret-admin-token\n");
        assertEquals(0, MasterHealthCheckMain.check(new String[] {"--config", config.toString()}));
        Files.writeString(token, "wrong-token\n");
        assertEquals(1, MasterHealthCheckMain.check(new String[] {"--config", config.toString()}));
        Files.writeString(token, "secret-admin-token\n");
        server.close();
        assertEquals(1, MasterHealthCheckMain.check(new String[] {"--config", config.toString()}));
        assertEquals(1, MasterHealthCheckMain.check(new String[] {}));
    }

    @Test
    void browsesOverRealSocketWithIdentityPow(@TempDir Path dir) throws Exception {
        server = MasterServer.start(testConfig(), dir);
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("id"));
        ClientHandshake handshake = new ClientHandshake(identity, "A", "0.6:cafe", null,
                com.openggf.net.protocol.AuthenticationTranscript.Role.MASTER,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT);
        Probe probe = new Probe();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/master"), probe)
                .get(10, TimeUnit.SECONDS);
        socket.sendText(ControlCodec.encode(null, handshake.hello()), true).join();
        ControlMessage.Welcome welcome = (ControlMessage.Welcome) await(
                probe, ControlMessage.Welcome.class);
        socket.sendText(ControlCodec.encode(null, handshake.onWelcome(welcome)), true).join();
        ControlMessage.PowChallenge challenge = (ControlMessage.PowChallenge) await(
                probe, ControlMessage.PowChallenge.class);
        socket.sendText(ControlCodec.encode(null, new ControlMessage.PowSolution("IDENTITY",
                identity.creationPowNonce(challenge.difficultyBits()))), true).join();
        ControlMessage.JoinAccepted accepted = (ControlMessage.JoinAccepted) await(
                probe, ControlMessage.JoinAccepted.class);

        socket.sendText(ControlCodec.encode(accepted.sessionToken(),
                new ControlMessage.RoomListRequest(null, 0)), true).join();
        ControlMessage.RoomListResult list = (ControlMessage.RoomListResult) await(
                probe, ControlMessage.RoomListResult.class);
        assertTrue(list.rooms().isEmpty());
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    }

    @Test
    void adminEndpointSanctionsAndRequiresAuthorization(@TempDir Path dir) throws Exception {
        server = MasterServer.start(testConfig(), dir);
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> unauthorized = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + server.adminPort()
                                + "/admin/attack-mode"))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"enabled\":true}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthorized.statusCode());

        HttpResponse<String> ok = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + server.adminPort()
                                + "/admin/sanction"))
                        .header("Authorization", "Bearer secret-admin-token")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"fingerprint":"abc","type":"BAN","reason":"cheating","durationHours":0}
                                """))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ok.statusCode());
        assertTrue(java.nio.file.Files.readString(dir.resolve("admin-audit.jsonl"))
                .contains("cheating"));
    }

    @Test
    void adminTimeoutImmediatelyRevokesAnActiveSession(@TempDir Path dir) throws Exception {
        server = MasterServer.start(testConfig(), dir);
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("id"));
        ClientHandshake handshake = new ClientHandshake(identity, "A", "0.6:cafe", null,
                com.openggf.net.protocol.AuthenticationTranscript.Role.MASTER,
                () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT);
        Probe probe = new Probe();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + server.port() + "/master"), probe)
                .get(10, TimeUnit.SECONDS);
        socket.sendText(ControlCodec.encode(null, handshake.hello()), true).join();
        ControlMessage.Welcome welcome = (ControlMessage.Welcome) await(
                probe, ControlMessage.Welcome.class);
        socket.sendText(ControlCodec.encode(null, handshake.onWelcome(welcome)), true).join();
        ControlMessage.PowChallenge challenge = (ControlMessage.PowChallenge) await(
                probe, ControlMessage.PowChallenge.class);
        socket.sendText(ControlCodec.encode(null, new ControlMessage.PowSolution("IDENTITY",
                identity.creationPowNonce(challenge.difficultyBits()))), true).join();
        String token = ((ControlMessage.JoinAccepted) await(probe,
                ControlMessage.JoinAccepted.class)).sessionToken();

        HttpResponse<String> result = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + server.adminPort()
                                + "/admin/sanction"))
                        .header("Authorization", "Bearer secret-admin-token")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"fingerprint\":\""
                                + identity.fingerprint() + "\",\"type\":\"TIMEOUT\","
                                + "\"reason\":\"spam\",\"durationHours\":1}"))
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, result.statusCode());
        assertTrue(server.broker().fingerprintForSessionToken(token).isEmpty());
    }
}
