package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.client.GhostStreamPublisher;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Seeded state/operation coverage of the room boundary, without sockets or timing assertions. */
@Timeout(90)
class TestRoomHostAdversarial {
    private static final long SEED = 0x5241434553545245L;
    private static final String FP = "stateful-room-test";
    private static final ControlJsonCodec CODEC = new ControlJsonCodec();
    private static final ControlMessage.RoundConfig CONFIG =
            new ControlMessage.RoundConfig("s3k", 0, 0, 1, "OPEN", null);

    private enum State {
        NEW, CHALLENGED, LOBBY, COUNTDOWN, RUNNING, ACTIVE, RESET, FINISHED,
        ROUND_END, VOTING, DISCONNECTED
    }

    private enum Attack {
        MALFORMED, TRAILING_ROOT, WRAPPED_VERSION, PROOF_REPLAY, HELLO_REPLAY,
        STOLEN_TOKEN, WRONG_TOKEN, CONFIGURE_CURRENT, CONFIGURE_FOREIGN,
        FORGED_ROSTER, FORGED_STANDINGS, FORGED_ADMISSION, WRONG_START,
        WRONG_RESET, WRONG_FINISH, UNKNOWN_VOTE, OVERSIZED_CHARACTER,
        BINARY_EMPTY, BINARY_OVERSIZED, BINARY_WRONG_ATTEMPT, BINARY_SERVER_PACKET,
        VALID_PING
    }

    @TempDir Path dir;

    @Test
    void hostileOperationsAtEveryStatePreserveAuthorityAndHealthyPeerProgress() throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("host"));
        PlayerIdentity healthy = PlayerIdentity.loadOrCreate(dir.resolve("healthy"));
        PlayerIdentity attacker = PlayerIdentity.loadOrCreate(dir.resolve("attacker"));
        Random random = new Random(SEED);
        for (State state : State.values()) {
            List<Attack> attacks = new ArrayList<>(List.of(Attack.values()));
            Collections.shuffle(attacks, random);
            for (Attack attack : attacks) {
                String context = "seed=" + SEED + " state=" + state + " operation=" + attack;
                assertDoesNotThrow(() -> exercise(host, healthy, attacker, state, attack, context), context);
            }
        }
    }

    private void exercise(PlayerIdentity host, PlayerIdentity healthy, PlayerIdentity attacker,
                          State state, Attack attack, String context) throws GeneralSecurityException {
        Harness harness = new Harness(host, 8, state == State.VOTING);
        Peer witness = harness.join(healthy, "Witness");
        Peer bad = harness.connect(attacker, "Attacker");
        if (state != State.NEW) harness.hello(bad);
        if (state != State.NEW && state != State.CHALLENGED) harness.prove(bad);
        if (state.ordinal() >= State.COUNTDOWN.ordinal() && state != State.DISCONNECTED) {
            assertTrue(harness.room.requestStartRound(CONFIG), context);
            if (state != State.COUNTDOWN) {
                harness.advance(HostRoundEngine.COUNTDOWN_MILLIS);
            }
        }
        if (state == State.ACTIVE || state == State.RESET) {
            harness.control(bad, new ControlMessage.AttemptStart(1));
            if (state == State.RESET) harness.control(bad, new ControlMessage.AttemptReset(1));
        }
        if (state == State.FINISHED) harness.finish(bad, 1);
        if (state == State.ROUND_END || state == State.VOTING) {
            harness.advance(1000 + HostRoundEngine.FINISH_GRACE_MILLIS + 1);
            if (state == State.VOTING) harness.advance(HostRoundEngine.ROUND_END_LINGER_MILLIS);
        }
        if (state == State.DISCONNECTED) harness.disconnect(bad);
        assertEquals(expectedPhase(state), harness.room.round().phase(), context);

        // Warm controls prove the witness can observe real delivery and that the roster oracle
        // detects legitimate state changes before rejecting mutations from the wrong authority.
        harness.control(witness, new ControlMessage.Ping(42));
        assertTrue(witness.messages.stream().anyMatch(message -> message instanceof ControlMessage.Pong pong
                && pong.t0ClientMillis() == 42), context);
        if (harness.room.round().phase() == HostRoundEngine.Phase.LOBBY) {
            harness.control(witness, new ControlMessage.SelectCharacter("tails"));
            assertEquals("tails", harness.room.players().getFirst().character(), context);
        }
        harness.room.tick();
        if (state == State.FINISHED) assertTrue(witness.binaryCount > 0, context);
        witness.binaryCount = 0;
        var descriptor = harness.room.descriptor();
        var standings = harness.room.round().standings();
        var roster = harness.room.players();
        var witnessInfo = harness.room.players().stream()
                .filter(player -> player.slot() == witness.join.playerSlot()).findFirst().orElseThrow();
        var phase = harness.room.round().phase();
        int previousPlayers = harness.room.playerCount();

        for (int step = 0; step < 12; step++) {
            apply(harness, bad, witness, attack);
            harness.flushDisconnect(bad);
            harness.room.tick();
            harness.flushDisconnect(bad);
            String at = context + " step=" + step;
            assertFalse(witness.closed, at);
            assertEquals(descriptor, harness.room.descriptor(), at);
            assertEquals(phase, harness.room.round().phase(), at);
            assertEquals(standings, harness.room.round().standings(), at);
            assertTrue(harness.room.players().contains(witnessInfo), at);
            assertTrue(roster.containsAll(harness.room.players()), at);
            assertEquals(witness.join.sessionToken(), witness.lastAdmission.sessionToken(), at);
            assertEquals(0, witness.binaryCount, "hostile stream relayed: " + at);
            int currentPlayers = harness.room.playerCount();
            assertTrue(currentPlayers <= previousPlayers && currentPlayers >= 1, at);
            assertEquals(currentPlayers, harness.room.players().stream()
                    .map(ControlMessage.PlayerInfo::slot).distinct().count(), at);
            previousPlayers = currentPlayers;
            if (attack == Attack.VALID_PING && !bad.closed) {
                assertEquals(new ControlMessage.Pong(123, harness.now), bad.messages.getLast(), at);
            }
            if (bad.closed) break;
        }

        boolean ignoredOrAllowed = switch (attack) {
            case CONFIGURE_CURRENT, CONFIGURE_FOREIGN, UNKNOWN_VOTE, OVERSIZED_CHARACTER, VALID_PING -> true;
            default -> false;
        };
        if (!ignoredOrAllowed || state == State.NEW || state == State.CHALLENGED
                || state == State.DISCONNECTED) {
            assertTrue(bad.closed, "expected protocol rejection: " + context);
        } else {
            assertFalse(bad.closed, "permitted or ignored operation disconnected peer: " + context);
        }

        harness.disconnect(bad);
        assertEquals(1, harness.room.playerCount(), context);
        // A valid finish after removal is the isolation oracle, including after end/vote cleanup.
        if (harness.room.round().phase() == HostRoundEngine.Phase.COUNTDOWN) {
            harness.advance(HostRoundEngine.COUNTDOWN_MILLIS);
        }
        if (harness.room.round().phase() == HostRoundEngine.Phase.ROUND_END) {
            harness.advance(HostRoundEngine.ROUND_END_LINGER_MILLIS);
        }
        if (harness.room.round().phase() == HostRoundEngine.Phase.VOTE) {
            harness.advance(HostRoundEngine.VOTE_WINDOW_MILLIS);
        }
        if (harness.room.round().phase() == HostRoundEngine.Phase.LOBBY) {
            assertTrue(harness.room.requestStartRound(CONFIG), context);
            harness.advance(HostRoundEngine.COUNTDOWN_MILLIS);
        }
        harness.finish(witness, 1);
        assertTrue(harness.room.round().standings().stream()
                .anyMatch(row -> row.slot() == witness.join.playerSlot()
                        && row.displayName().equals("Witness") && row.bestTimeFrames() == 1), context);
        harness.room.close("test complete");
        harness.flushDisconnect(witness);
        assertEquals(0, harness.room.playerCount(), context);
    }

    private static HostRoundEngine.Phase expectedPhase(State state) {
        return switch (state) {
            case NEW, CHALLENGED, LOBBY, DISCONNECTED -> HostRoundEngine.Phase.LOBBY;
            case COUNTDOWN -> HostRoundEngine.Phase.COUNTDOWN;
            case RUNNING, ACTIVE, RESET, FINISHED -> HostRoundEngine.Phase.RUNNING;
            case ROUND_END -> HostRoundEngine.Phase.ROUND_END;
            case VOTING -> HostRoundEngine.Phase.VOTE;
        };
    }

    private static void apply(Harness harness, Peer bad, Peer witness, Attack attack) {
        String token = bad.join == null ? "unadmitted" : bad.join.sessionToken();
        switch (attack) {
            case MALFORMED -> harness.text(bad, "{\"v\":2,\"token\":null,\"msg\":null}");
            case TRAILING_ROOT -> harness.text(bad, CODEC.encode(token, new ControlMessage.Ping(1)) + " {}");
            case WRAPPED_VERSION -> harness.text(bad, CODEC.encode(token, new ControlMessage.Ping(1))
                    .replace("\"v\":2", "\"v\":4294967298"));
            case PROOF_REPLAY -> harness.control(bad, bad.proof == null
                    ? new ControlMessage.AuthProof("AA==") : bad.proof);
            case HELLO_REPLAY -> harness.control(bad, bad.handshake.hello());
            case STOLEN_TOKEN -> harness.text(bad, CODEC.encode(witness.join.sessionToken(),
                    new ControlMessage.SelectCharacter("knuckles")));
            case WRONG_TOKEN -> harness.text(bad, CODEC.encode("wrong", new ControlMessage.RoundConfigure(CONFIG)));
            case CONFIGURE_CURRENT -> harness.control(bad, new ControlMessage.RoundConfigure(CONFIG));
            case CONFIGURE_FOREIGN -> harness.control(bad, new ControlMessage.RoundConfigure(
                    new ControlMessage.RoundConfig("s2", 9, 1, 999, "LOCKED", "knuckles")));
            case FORGED_ROSTER -> harness.control(bad, new ControlMessage.RoomState(List.of()));
            case FORGED_STANDINGS -> harness.control(bad, new ControlMessage.StandingsDelta(List.of(
                    new ControlMessage.StandingsRow(witness.join.playerSlot(), "Impostor", "sonic", 1, 1, "VERIFIED"))));
            case FORGED_ADMISSION -> harness.control(bad, witness.join);
            case WRONG_START -> harness.control(bad, new ControlMessage.AttemptStart(-1));
            case WRONG_RESET -> harness.control(bad, new ControlMessage.AttemptReset(999));
            case WRONG_FINISH -> harness.control(bad, new ControlMessage.AttemptFinish(
                    999, 1, 0, 1, "ab".repeat(32), "cd".repeat(32), null));
            case UNKNOWN_VOTE -> harness.control(bad, new ControlMessage.TrackVote("s2:99:99"));
            case OVERSIZED_CHARACTER -> harness.control(bad, new ControlMessage.SelectCharacter("x".repeat(33)));
            case BINARY_EMPTY -> harness.room.onBinary(bad, new byte[0]);
            case BINARY_OVERSIZED -> harness.room.onBinary(bad, new byte[Protocol.MAX_BINARY_BYTES + 1]);
            case BINARY_WRONG_ATTEMPT -> harness.room.onBinary(bad,
                    GhostPackets.encodeFrames(999, 0, new byte[GhostFrameCodec.BYTES]));
            case BINARY_SERVER_PACKET -> harness.room.onBinary(bad, GhostPackets.encodeRoster(List.of(
                    new GhostPackets.RosterEntry(0, 1, 1, GhostPackets.ROSTER_STATUS_FINISHED))));
            case VALID_PING -> harness.control(bad, new ControlMessage.Ping(123));
        }
    }

    @Test
    void fullRelayOccupancyRejectsOverflowAndReusesDepartedSlotsWithoutIdentityLeakage() throws Exception {
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dir.resolve("capacity-host"));
        Harness harness = new Harness(host, Protocol.MAX_PLAYERS_RELAY, false);
        List<Peer> peers = new ArrayList<>();
        List<PlayerIdentity> identities = new ArrayList<>();
        var tokens = new HashSet<String>();
        for (int index = 0; index < Protocol.MAX_PLAYERS_RELAY; index++) {
            PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("capacity-" + index));
            identities.add(identity);
            Peer peer = harness.join(identity, "Player-" + index);
            peers.add(peer);
            assertEquals(index, peer.join.playerSlot(), "admission " + index);
            assertTrue(tokens.add(peer.join.sessionToken()), "duplicate token " + index);
        }
        assertEquals(256, harness.room.playerCount());
        assertEquals(256, harness.room.players().stream().map(ControlMessage.PlayerInfo::fingerprint).distinct().count());
        Peer overflow = harness.connect(host, "Overflow");
        harness.hello(overflow);
        harness.prove(overflow);
        assertTrue(overflow.closed);
        assertNull(overflow.join);
        assertTrue(overflow.messages.stream().anyMatch(ControlMessage.JoinRejected.class::isInstance));
        harness.flushDisconnect(overflow);
        assertEquals(256, harness.room.playerCount());

        for (int index = 0; index < 256; index += 17) {
            String replacementName = "Replacement-" + index;
            Peer departed = peers.get(index);
            harness.disconnect(departed);
            Peer replacement = harness.join(identities.get(index), replacementName);
            assertEquals(index, replacement.join.playerSlot());
            assertNotEquals(departed.join.sessionToken(), replacement.join.sessionToken());
            harness.text(departed, CODEC.encode(departed.join.sessionToken(), new ControlMessage.Chat("stale")));
            assertEquals(256, harness.room.playerCount());
            assertEquals(256, harness.room.players().stream().map(ControlMessage.PlayerInfo::slot).distinct().count());
            assertTrue(harness.room.players().stream().anyMatch(player -> player.displayName().equals(replacementName)));
        }
        String roster = CODEC.encode(null, new ControlMessage.RoomState(harness.room.players()));
        assertEquals(256, ((ControlMessage.RoomState) CODEC.decodeRoom(roster).message()).players().size());
        harness.room.close("capacity complete");
        assertEquals(0, harness.room.playerCount());
    }

    private static final class Harness {
        long now = 1_000_000;
        final RoomHost room;

        Harness(PlayerIdentity host, int capacity, boolean voting) {
            room = new RoomHost(new RoomHostConfig("Adversarial", "s3k", 0, 0,
                    "OPEN", null, capacity, FP, voting ? List.of("s3k:1:0", "s3k:2:0") : List.of()),
                    host, () -> now, TrackValidationProfileSource.none());
        }

        Peer connect(PlayerIdentity identity, String name) {
            Peer peer = new Peer(new ClientHandshake(identity, name, FP));
            room.onConnected(peer);
            return peer;
        }

        void hello(Peer peer) { text(peer, CODEC.encode(null, peer.handshake.hello())); }

        void prove(Peer peer) throws GeneralSecurityException {
            peer.proof = peer.handshake.onWelcome(peer.welcome);
            text(peer, CODEC.encode(null, peer.proof));
            peer.join = peer.lastAdmission;
        }

        Peer join(PlayerIdentity identity, String name) throws GeneralSecurityException {
            Peer peer = connect(identity, name);
            hello(peer);
            prove(peer);
            assertNotNull(peer.join, name);
            assertFalse(peer.closed, name);
            return peer;
        }

        void control(Peer peer, ControlMessage message) {
            text(peer, CODEC.encode(peer.join == null ? null : peer.join.sessionToken(), message));
        }

        void text(Peer peer, String value) { room.onText(peer, value); }

        void advance(long millis) { now += millis; room.tick(); }

        void disconnect(Peer peer) {
            peer.closed = true;
            room.onDisconnected(peer);
            peer.disconnectDelivered = true;
        }

        void flushDisconnect(Peer peer) {
            if (peer.closed && !peer.disconnectDelivered) disconnect(peer);
        }

        void finish(Peer peer, int attempt) {
            control(peer, new ControlMessage.AttemptStart(attempt));
            GhostStreamPublisher publisher = new GhostStreamPublisher(packet -> room.onBinary(peer, packet));
            publisher.beginAttempt(attempt);
            publisher.onFrame(new GhostFrame(100, 100, 1, false, false, false, 0, false));
            publisher.onFrame(new GhostFrame(101, 100, 1, false, false, false, 0, false));
            publisher.finishAttempt();
            control(peer, new ControlMessage.AttemptFinish(attempt, 1, 0, 1,
                    "ab".repeat(32), HexFormat.of().formatHex(publisher.streamHashSha256()), null));
        }
    }

    /** Fixed-size observations; transport disconnect is delivered after the owner call returns. */
    private static final class Peer implements HubConnection, HandshakeTransport {
        final ClientHandshake handshake;
        final ArrayDeque<ControlMessage> messages = new ArrayDeque<>();
        ControlMessage.Welcome welcome;
        ControlMessage.AuthProof proof;
        ControlMessage.JoinAccepted lastAdmission;
        ControlMessage.JoinAccepted join;
        boolean closed;
        boolean disconnectDelivered;
        int binaryCount;

        Peer(ClientHandshake handshake) { this.handshake = handshake; }

        @Override public void sendText(String text) {
            ControlMessage message = CODEC.decodeRoom(text).message();
            if (message instanceof ControlMessage.Welcome value) welcome = value;
            if (message instanceof ControlMessage.JoinAccepted value) lastAdmission = value;
            if (messages.size() == 8) messages.removeFirst();
            messages.addLast(message);
        }

        @Override public void sendBinary(byte[] bytes) { binaryCount++; }
        @Override public void close(String reason) { closed = true; }
        @Override public String remoteHost() { return "model-peer"; }
        @Override public String authenticationBinding() { return AuthenticationTranscript.LOCAL_PLAINTEXT; }
    }
}
