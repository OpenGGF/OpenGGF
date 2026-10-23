package com.openggf.net.client;

import com.openggf.net.hub.HostHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.AuthenticationTranscript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestHandshakeIntentBinding {
    @Test
    void malformedUnicodeCannotAliasASignedQuestionMark(@TempDir Path dir) throws Exception {
        var identity = PlayerIdentity.loadOrCreate(dir);
        for (String name : new String[]{"\uD800", "\uDC00"}) {
            var client = new ClientHandshake(identity, "?", "build");
            var hello = client.hello();
            var altered = new ControlMessage.Hello(hello.protocolVersion(), hello.pubKeyBase64(), name,
                    hello.determinismFingerprint(), hello.appearance());
            var host = new HostHandshake("authority", null);
            assertInstanceOf(HostHandshake.Reject.class, host.onHello(altered),
                    "UTF-8 replacement must not collapse different Hello intent into the same transcript");
        }
        var valid = new ClientHandshake(identity, "Player 🦔", "build");
        var authority = new HostHandshake("authority", null);
        var welcome = assertInstanceOf(HostHandshake.SendWelcome.class, authority.onHello(valid.hello()));
        assertInstanceOf(HostHandshake.Admit.class, authority.onAuthProof(valid.onWelcome(welcome.welcome())));
    }

    @Test
    void absentTransportMetadataCannotSilentlySelectThePlaintextDomain() {
        var unknown = new com.openggf.net.hub.HubConnection() {
            public void sendText(String text) { }
            public void sendBinary(byte[] data) { }
            public void close(String reason) { }
            public String remoteHost() { return "127.0.0.1"; }
        };
        assertThrows(IllegalArgumentException.class, () -> com.openggf.net.hub.HandshakeTransport.binding(unknown));
    }

    @Test
    void relayedNonceCannotAuthenticateAnotherRoleOrTlsEndpoint(@TempDir Path dir) throws Exception {
        var identity = PlayerIdentity.loadOrCreate(dir);
        for (var targetRole : AuthenticationTranscript.Role.values()) {
            for (var sourceRole : AuthenticationTranscript.Role.values()) {
                for (boolean sameCertificate : new boolean[]{true, false}) {
                    String expectedBinding = "tls-leaf-sha256:" + "ab".repeat(32);
                    String observedBinding = "tls-leaf-sha256:" + (sameCertificate ? "ab" : "cd").repeat(32);
                    var victim = new ClientHandshake(identity, "Alice", "build", null, sourceRole, () -> observedBinding);
                    var authority = new HostHandshake("authority", null, targetRole, expectedBinding);
                    var welcome = assertInstanceOf(HostHandshake.SendWelcome.class, authority.onHello(victim.hello()));
                    var result = authority.onAuthProof(victim.onWelcome(welcome.welcome()));
                    if (targetRole == sourceRole && sameCertificate) assertInstanceOf(HostHandshake.Admit.class, result);
                    else assertInstanceOf(HostHandshake.Reject.class, result,
                            sourceRole + " must not authenticate " + targetRole + " on another channel");
                }
            }
        }
    }

    @Test
    void authorityAndPlaintextSubstitutionAreRejected(@TempDir Path dir) throws Exception {
        var identity = PlayerIdentity.loadOrCreate(dir);
        var victim = new ClientHandshake(identity, "Alice", "build");
        var authority = new HostHandshake("legitimate", null);
        var welcome = assertInstanceOf(HostHandshake.SendWelcome.class, authority.onHello(victim.hello())).welcome();
        var rewritten = new ControlMessage.Welcome(welcome.protocolVersion(), welcome.nonceBase64(), "attacker");
        assertInstanceOf(HostHandshake.Reject.class, authority.onAuthProof(victim.onWelcome(rewritten)));
        var local = new ClientHandshake(identity, "Alice", "build");
        var tlsAuthority = new HostHandshake("authority", null, AuthenticationTranscript.Role.ROOM_DIRECT,
                "tls-leaf-sha256:" + "ab".repeat(32));
        var challenge = assertInstanceOf(HostHandshake.SendWelcome.class, tlsAuthority.onHello(local.hello()));
        assertInstanceOf(HostHandshake.Reject.class, tlsAuthority.onAuthProof(local.onWelcome(challenge.welcome())));
    }

    @Test
    void obsoleteNonceAndServerIdProofIsNeverAccepted(@TempDir Path dir) throws Exception {
        var identity = PlayerIdentity.loadOrCreate(dir);
        var client = new ClientHandshake(identity, "Alice", "build");
        var host = new HostHandshake("authority", null);
        var welcome = assertInstanceOf(HostHandshake.SendWelcome.class, host.onHello(client.hello())).welcome();
        byte[] nonce = java.util.Base64.getDecoder().decode(welcome.nonceBase64());
        byte[] authority = "authority".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] legacy = java.util.Arrays.copyOf(nonce, nonce.length + authority.length);
        System.arraycopy(authority, 0, legacy, nonce.length, authority.length);
        var proof = new ControlMessage.AuthProof(java.util.Base64.getEncoder().encodeToString(identity.sign(legacy)));
        assertInstanceOf(HostHandshake.Reject.class, host.onAuthProof(proof));
    }

    @Test
    void observationFailsClosedWithoutOneValidatedConnection() throws Exception {
        var observed = new ObservedTlsContext(javax.net.ssl.SSLContext.getDefault());
        assertThrows(IllegalStateException.class, observed::binding);
        observed.createSSLEngine("example.com", 443);
        assertThrows(IllegalStateException.class, observed::binding);
        observed.createSSLEngine("example.com", 443);
        assertThrows(IllegalStateException.class, observed::binding);
    }
    @Test
    void plaintextConnectRejectsRemoteHostsBeforeNetworkWork(@TempDir Path dir) throws Exception {
        var identity = PlayerIdentity.loadOrCreate(dir);
        for (String uri : new String[]{"ws://example.com/race", "ws://localhost/race", "ws://127.1/race",
                "wss://user@example.com/race", "wss://example.com/race#fragment"}) {
            var room = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> RaceClient.connect(java.net.URI.create(uri), identity, "A", "build").get());
            assertInstanceOf(IllegalArgumentException.class, room.getCause(), uri);
            var master = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> MasterClient.connect(java.net.URI.create(uri), identity, "A", "build", null).get());
            assertInstanceOf(IllegalArgumentException.class, master.getCause(), uri);
        }
    }

    @Test
    void relayCannotReplaceDisplayNameAppearanceOrDeterminism(@TempDir Path dir) throws Exception {
        var identity = PlayerIdentity.loadOrCreate(dir);
        for (int replaced = 0; replaced < 3; replaced++) {
            var client = new ClientHandshake(identity, "Alice", "0E6.20E6", "build-a", null,
                    com.openggf.net.protocol.AuthenticationTranscript.Role.ROOM_DIRECT,
                    () -> com.openggf.net.protocol.AuthenticationTranscript.LOCAL_PLAINTEXT);
            var hello = client.hello();
            var substituted = new ControlMessage.Hello(hello.protocolVersion(), hello.pubKeyBase64(),
                    replaced == 0 ? "Mallory" : hello.displayName(),
                    replaced == 1 ? "build-b" : hello.determinismFingerprint(),
                    replaced == 2 ? "" : hello.appearance());
            var host = new HostHandshake("authority", null);
            var welcome = assertInstanceOf(HostHandshake.SendWelcome.class, host.onHello(substituted));
            assertInstanceOf(HostHandshake.Reject.class, host.onAuthProof(client.onWelcome(welcome.welcome())),
                    "the authority must verify precisely the Hello the client intended");
        }
    }
}
