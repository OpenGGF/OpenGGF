package com.openggf.net.client;

import com.openggf.net.host.jdk.JdkRaceHostServer;
import com.openggf.net.hub.*;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.SSLHandshakeException;
import static org.junit.jupiter.api.Assertions.*;

class TestPrivateRoomClient {
    @TempDir Path dir;
    static final String PASSWORD = "a sufficiently long password";
    static final RoomAccessPolicy PRIVATE = new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true);

    JdkRaceHostServer start(PlayerIdentity owner) {
        try (RoomPassword password = RoomPassword.of(PASSWORD)) {
            return JdkRaceHostServer.startTls(0, new RoomHostConfig("Private", "s3k", 0, 0,
                    "OPEN", null, 8, "test", List.of(), false, PRIVATE, password, null),
                    owner, TrackValidationProfileSource.none());
        }
    }

    URI uri(JdkRaceHostServer host) { return URI.create("wss://127.0.0.1:" + host.port() + "/race"); }

    int count(JdkRaceHostServer host) throws Exception {
        CompletableFuture<Integer> count = new CompletableFuture<>();
        host.execute(() -> count.complete(host.room().playerCount()));
        return count.get(2, TimeUnit.SECONDS);
    }

    @Test
    void manualProbeClosesBeforePromptAndCorrectPasswordJoinsSamePinnedInstance() throws Exception {
        PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        try (var host = start(owner)) {
            var failed = RaceClient.connect(uri(host), guest, "Guest", "test",
                    host.tlsCertificateSha256(), owner.fingerprint());
            RoomAccessRequiredException required = assertInstanceOf(RoomAccessRequiredException.class,
                    assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS)).getCause());
            assertEquals(host.room().instanceId(), required.instanceId());
            assertEquals(0, required.accessEpoch());
            assertEquals(PRIVATE, required.accessPolicy());
            assertEquals(0, count(host));
            try (RoomPassword password = RoomPassword.of(PASSWORD);
                 RoomJoinOptions options = new RoomJoinOptions(password, required.instanceId(), required.accessEpoch())) {
                RaceClient joined = RaceClient.connect(uri(host), guest, "Guest", "", "test",
                        host.tlsCertificateSha256(), owner.fingerprint(), options).get(5, TimeUnit.SECONDS);
                try {
                    assertTrue(joined.isOpen());
                    assertEquals(1, count(host));
                    assertTrue(joined.joinAccepted().room().accessPolicy().passwordRequired());
                } finally { joined.close(); }
            }
        }
    }

    @Test
    void mismatchedInstanceOrEpochNeverSubmitsPasswordAndDoesNotConsumePasswordBudget() throws Exception {
        PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        try (var host = start(owner); RoomPassword password = RoomPassword.of(PASSWORD)) {
            for (int i = 0; i < 6; i++) {
                try (var options = new RoomJoinOptions(password, i % 2 == 0 ? "replacement" : host.room().instanceId(),
                        i % 2 == 0 ? 0 : 1)) {
                    assertInstanceOf(RaceClient.JoinRejectedException.class,
                            assertThrows(ExecutionException.class, () -> RaceClient.connect(uri(host), guest,
                                    "Guest", "", "test", host.tlsCertificateSha256(), owner.fingerprint(), options)
                                    .get(5, TimeUnit.SECONDS)).getCause());
                }
            }
            try (var options = new RoomJoinOptions(password, host.room().instanceId(), 0)) {
                var joined = RaceClient.connect(uri(host), guest, "Guest", "", "test",
                        host.tlsCertificateSha256(), owner.fingerprint(), options).get(5, TimeUnit.SECONDS);
                try {
                    assertTrue(joined.isOpen());
                    assertEquals(1, count(host));
                } finally { joined.close(); }
            }
        }
    }

    ControlMessage.RoomJoinResult prepared(JdkRaceHostServer host, PlayerIdentity owner, String pin) {
        return new ControlMessage.RoomJoinResult("room", "DIRECT", "127.0.0.1", host.port(), owner.fingerprint(),
                "test", pin, host.room().instanceId(), PRIVATE, 0, 1, System.currentTimeMillis()+600_000);
    }

    @Test
    void wrongPasswordAndWrongCertificateNeverAttemptRelay() throws Exception {
        PlayerIdentity owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        PlayerIdentity guest = PlayerIdentity.loadOrCreate(dir.resolve("guest"));
        try (var host = start(owner)) {
            for (boolean badPin : new boolean[]{false, true}) {
                var socket = new TestMasterClientTimeoutRecovery.SilentSocket();
                try (MasterClient master = TestMasterClientTimeoutRecovery.admittedClient(socket);
                     RoomPassword password = RoomPassword.of(badPin ? PASSWORD : "the wrong room password");
                     RoomJoinOptions options = new RoomJoinOptions(password, host.room().instanceId(), 0)) {
                    var result = prepared(host, owner, badPin ? "ab".repeat(32) : host.tlsCertificateSha256());
                    assertThrows(ExecutionException.class, () -> master.joinResolved(result, guest,
                            "Guest", "", "test", options).get(5, TimeUnit.SECONDS));
                    assertTrue(socket.sent.isEmpty(), "terminal rejection must never send RelayAttach");
                    assertEquals(0, count(host));
                }
            }
            var socket = new TestMasterClientTimeoutRecovery.SilentSocket();
            try (MasterClient master = TestMasterClientTimeoutRecovery.admittedClient(socket);
                 RoomPassword password = RoomPassword.of(PASSWORD);
                 RoomJoinOptions options = new RoomJoinOptions(password, host.room().instanceId(), 0)) {
                RaceConnection joined = master.joinResolved(prepared(host, owner, host.tlsCertificateSha256()),
                        guest, "Guest", "", "test", options).get(5, TimeUnit.SECONDS);
                try {
                    assertTrue(joined instanceof RaceClient);
                    assertTrue(joined.isOpen());
                    assertEquals(1, count(host));
                    assertTrue(socket.sent.isEmpty());
                } finally { joined.close(); }
            }
        }
    }

    @Test
    void classifiedConnectionRefusalUsesPreparedRelayGrant() throws Exception {
        int unavailablePort;
        try (ServerSocket reservation = new ServerSocket(0)) { unavailablePort = reservation.getLocalPort(); }
        var socket = new TestMasterClientTimeoutRecovery.SilentSocket();
        var codec = new ControlJsonCodec();
        try (MasterClient master = TestMasterClientTimeoutRecovery.admittedClient(socket);
             RoomJoinOptions options = new RoomJoinOptions(null, "instance", 0)) {
            socket.onSend = text -> {
                if (codec.decode(text).message() instanceof ControlMessage.RelayAttach) {
                    assertDoesNotThrow(() -> TestMasterClientTimeoutRecovery.receive(master, socket,
                            new ControlMessage.RoomJoinRejected("relay unavailable"), 0));
                }
            };
            var prepared = new ControlMessage.RoomJoinResult("room", "DIRECT", "127.0.0.1", unavailablePort,
                    "host", "test", "ab".repeat(32), "instance", RoomAccessPolicy.PUBLIC, 0, 0, 0);
            assertThrows(ExecutionException.class, () -> master.joinResolved(prepared,
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Guest", "", "test", options)
                    .get(5, TimeUnit.SECONDS));
            assertEquals(1, socket.sent.stream().map(codec::decode)
                    .filter(frame -> frame.message() instanceof ControlMessage.RelayAttach).count());
        }
    }

    @Test
    void reResolutionAfterInputRejectsInstanceEpochPinOrInviteReplacementBeforeConnecting() throws Exception {
        for (int change = 0; change < 4; change++) {
            var socket = new TestMasterClientTimeoutRecovery.SilentSocket();
            try (MasterClient master = TestMasterClientTimeoutRecovery.admittedClient(socket);
                 RoomPassword password = RoomPassword.of(PASSWORD)) {
                long expiry = System.currentTimeMillis()+600_000;
                var expected = new ControlMessage.RoomJoinResult("room", "DIRECT", "127.0.0.1", 9,
                        "host", "test", "ab".repeat(32), "instance", PRIVATE, 0, 1, expiry);
                var operation = master.joinInvite("7K3M9X", expected,
                        PlayerIdentity.loadOrCreate(dir.resolve("guest"+change)), "Guest", "", "test", password);
                long id = new ControlJsonCodec().decode(socket.sent.getFirst()).requestId();
                var fresh = new ControlMessage.RoomJoinResult("room", "DIRECT", "127.0.0.1", 9,
                        "host", "test", change == 2 ? "cd".repeat(32) : "ab".repeat(32),
                        change == 0 ? "replacement" : "instance", PRIVATE, change == 1 ? 1 : 0,
                        change == 3 ? 2 : 1, expiry);
                TestMasterClientTimeoutRecovery.receive(master, socket, fresh, id);
                assertInstanceOf(RaceClient.JoinRejectedException.class,
                        assertThrows(ExecutionException.class, () -> operation.get(2, TimeUnit.SECONDS)).getCause());
                assertEquals(1, socket.sent.size());
            }
        }
    }

    @Test
    void expiredPreparationCannotRenewInvitationAfterPasswordEntry() throws Exception {
        var socket = new TestMasterClientTimeoutRecovery.SilentSocket();
        try (MasterClient master = TestMasterClientTimeoutRecovery.admittedClient(socket);
             RoomPassword password = RoomPassword.of(PASSWORD)) {
            var expired = new ControlMessage.RoomJoinResult("room", "RELAY", null, 0, "host", "test", null,
                    "instance", PRIVATE, 0, 1, 1);
            assertThrows(ExecutionException.class, () -> master.joinInvite("7K3M9X", expired,
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Guest", "", "test", password)
                    .get(2, TimeUnit.SECONDS));
            assertTrue(socket.sent.isEmpty());
        }
    }

    @Test
    void canceledInvitePreparationNeverAttachesAfterAValidLateResolution() throws Exception {
        var socket = new TestMasterClientTimeoutRecovery.SilentSocket();
        var codec = new ControlJsonCodec();
        try (MasterClient master = TestMasterClientTimeoutRecovery.admittedClient(socket)) {
            var expected = new ControlMessage.RoomJoinResult("room", "RELAY", null, 0, "host", "test", null,
                    "instance", RoomAccessPolicy.PUBLIC, 0, 1, System.currentTimeMillis()+600_000);
            var joining = master.joinInvite("7K3M9X", expected,
                    PlayerIdentity.loadOrCreate(dir.resolve("guest")), "Guest", "", "test", null);
            long requestId = codec.decode(socket.sent.getFirst()).requestId();
            assertTrue(joining.cancel(false));
            TestMasterClientTimeoutRecovery.receive(master, socket, expected, requestId);
            assertTrue(joining.isCancelled());
            assertEquals(1, socket.sent.size());
            assertTrue(master.isOpen());
        }
    }

    @Test
    void fallbackClassificationExcludesSecurityProtocolAndPasswordFailuresEvenWithReachabilityCause() {
        assertTrue(MasterClient.allowsRelayFallback(new CompletionException(new ConnectException())));
        assertTrue(MasterClient.allowsRelayFallback(new UnknownHostException()));
        assertFalse(MasterClient.allowsRelayFallback(new TimeoutException()));
        assertFalse(MasterClient.allowsRelayFallback(new java.io.IOException()));
        for (Throwable terminal : List.of(new SSLHandshakeException("untrusted"),
                new java.security.GeneralSecurityException("identity"), new ProtocolViolationException("protocol"),
                new RaceClient.JoinRejectedException("denied"), new RoomInviteRejectedException(1000), new RoomAccessRequiredException("instance", 0, PRIVATE))) {
            terminal.initCause(new ConnectException());
            assertFalse(MasterClient.allowsRelayFallback(new CompletionException(terminal)));
        }
    }
}
