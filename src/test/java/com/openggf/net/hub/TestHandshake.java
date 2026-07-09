package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.Protocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestHandshake {
    private static final String FP = "0.6:cafe1234";

    @Test
    void happyPathAdmitsClient(@TempDir Path clientDir, @TempDir Path hostDir) throws Exception {
        PlayerIdentity client = PlayerIdentity.loadOrCreate(clientDir);
        PlayerIdentity host = PlayerIdentity.loadOrCreate(hostDir);
        ClientHandshake clientSide = new ClientHandshake(client, "Farrell", FP);
        HostHandshake hostSide = new HostHandshake(host.fingerprint(), FP);

        HostHandshake.Step step1 = hostSide.onHello(clientSide.hello());
        ControlMessage.Welcome welcome = ((HostHandshake.SendWelcome) step1).welcome();
        assertEquals(host.fingerprint(), welcome.serverId());

        HostHandshake.Step step2 = hostSide.onAuthProof(clientSide.onWelcome(welcome));
        HostHandshake.Admit admit = assertInstanceOf(HostHandshake.Admit.class, step2);
        assertEquals(client.fingerprint(), admit.fingerprint());
        assertEquals("Farrell", admit.displayName());
    }

    @Test
    void rejectsVersionAndFingerprintMismatch(@TempDir Path clientDir) throws Exception {
        PlayerIdentity client = PlayerIdentity.loadOrCreate(clientDir);
        ClientHandshake clientSide = new ClientHandshake(client, "x", FP);
        ControlMessage.Hello hello = clientSide.hello();

        HostHandshake wrongVersion = new HostHandshake("srv", FP);
        assertInstanceOf(HostHandshake.Reject.class, wrongVersion.onHello(
                new ControlMessage.Hello(Protocol.VERSION + 1, hello.pubKeyBase64(), "x", FP)));

        HostHandshake wrongRom = new HostHandshake("srv", "0.6:deadbeef");
        assertInstanceOf(HostHandshake.Reject.class, wrongRom.onHello(hello));
    }

    @Test
    void signatureBoundToServerIdCannotBeReplayedElsewhere(
            @TempDir Path clientDir, @TempDir Path hostDir) throws Exception {
        PlayerIdentity client = PlayerIdentity.loadOrCreate(clientDir);
        PlayerIdentity host = PlayerIdentity.loadOrCreate(hostDir);
        ClientHandshake clientSide = new ClientHandshake(client, "x", FP);

        HostHandshake serverA = new HostHandshake(host.fingerprint(), FP);
        ControlMessage.Welcome welcomeA = ((HostHandshake.SendWelcome)
                serverA.onHello(clientSide.hello())).welcome();
        ControlMessage.AuthProof proofForA = clientSide.onWelcome(welcomeA);

        HostHandshake serverB = new HostHandshake("differentserverfingerprint", FP);
        serverB.onHello(new ClientHandshake(client, "x", FP).hello());
        assertInstanceOf(HostHandshake.Reject.class, serverB.onAuthProof(proofForA));
    }

    @Test
    void outOfOrderMessagesReject(@TempDir Path hostDir) throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(hostDir);
        HostHandshake hostSide = new HostHandshake(host.fingerprint(), FP);
        assertInstanceOf(HostHandshake.Reject.class,
                hostSide.onAuthProof(new ControlMessage.AuthProof("c2ln")));
    }

    @Test
    void tokensIssueValidateAndRevoke() {
        SessionTokenIssuer issuer = new SessionTokenIssuer();
        String token = issuer.issue();
        assertEquals(32, token.length());
        assertTrue(issuer.isValid(token));
        assertFalse(issuer.isValid("deadbeef"));
        assertFalse(issuer.isValid(null));
        issuer.revoke(token);
        assertFalse(issuer.isValid(token));
        assertNotEquals(issuer.issue(), issuer.issue());
    }
}
