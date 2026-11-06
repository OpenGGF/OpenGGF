package com.openggf.net.hub;

import com.openggf.net.client.ClientHandshake;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ReadyRules;
import openggf.racing.protocol.ControlCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Readiness and lobby presence on the room host: SetReady/PlayerReady, the owner's ready rule
 * (REQUIRED, AUTO, DEFAULT_BY_SIZE with its five/six-player boundary), the owner's start gate,
 * and the between-round lobby pose relay with its validation.
 */
class TestRoomHostLobby {
    private static final String FP = "0.6:cafe1234";
    private static final ControlMessage.RoundConfig ROUND =
            new ControlMessage.RoundConfig("s3k", 0, 0, 60, "OPEN", null);

    @TempDir Path dir;
    private long now = 1_000_000;
    private RoomHost room;
    private PlayerIdentity owner;

    private record Member(FakeHubConnection connection, String token, int slot) { }

    @BeforeEach
    void setUp() throws Exception {
        owner = PlayerIdentity.loadOrCreate(dir.resolve("owner"));
        room = room(false);
    }

    private RoomHost room(boolean relevance) {
        return new RoomHost(new RoomHostConfig("LAN", "s3k", 0, 0, "OPEN", null, 64, FP), owner, () -> now,
                TrackValidationProfileSource.none(),
                new RoomHostHooks(relevance, null, null, null, null, null, null, null));
    }

    private Member admit(String name, Path identityDir) throws Exception {
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(identityDir);
        FakeHubConnection connection = new FakeHubConnection();
        ClientHandshake handshake = new ClientHandshake(identity, name, "", FP, null,
                AuthenticationTranscript.Role.ROOM_DIRECT, () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
        room.onConnected(connection);
        room.onText(connection, ControlCodec.encode(null, handshake.hello()));
        var welcome = (ControlMessage.Welcome) messages(connection).getLast();
        room.onText(connection, ControlCodec.encode(null, handshake.onWelcome(welcome)));
        var accepted = messages(connection).stream().filter(ControlMessage.JoinAccepted.class::isInstance)
                .map(ControlMessage.JoinAccepted.class::cast).findFirst().orElseThrow();
        return new Member(connection, accepted.sessionToken(), accepted.playerSlot());
    }

    private Member admitOwner() throws Exception {
        return admit("Owner", dir.resolve("owner"));
    }

    private List<Member> admitGuests(int count) throws Exception {
        List<Member> guests = new ArrayList<>();
        for (int i = 0; i < count; i++) guests.add(admit("G" + i, dir.resolve("g" + i)));
        return guests;
    }

    private void send(Member member, ControlMessage message) {
        room.onText(member.connection(), ControlCodec.encode(member.token(), message));
    }

    private static List<ControlMessage> messages(FakeHubConnection connection) {
        return connection.text.stream().map(text -> ControlCodec.decode(text).message()).toList();
    }

    private static <T extends ControlMessage> List<T> all(Member member, Class<T> type) {
        return messages(member.connection()).stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static <T extends ControlMessage> T last(Member member, Class<T> type) {
        List<T> found = all(member, type);
        assertFalse(found.isEmpty(), "no " + type.getSimpleName());
        return found.getLast();
    }

    // ── Ready and the ready rule ──────────────────────────────────────────

    @Test
    void readyIsBroadcastAndTheDefaultRuleWaitsForEveryoneInASmallRoom() throws Exception {
        Member host = admitOwner();
        Member guest = admitGuests(1).getFirst();
        assertEquals(new ControlMessage.ReadyRule("DEFAULT_BY_SIZE", true), last(guest, ControlMessage.ReadyRule.class));
        assertFalse(room.isReady(host.slot()));

        send(host, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.LOBBY, room.round().phase(), "nobody is ready yet");

        send(host, new ControlMessage.SetReady(true));
        assertEquals(new ControlMessage.PlayerReady(host.slot(), true), last(guest, ControlMessage.PlayerReady.class));
        send(host, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.LOBBY, room.round().phase(), "the guest is not ready");

        now += Protocol.READY_MIN_INTERVAL_MILLIS;
        send(guest, new ControlMessage.SetReady(true));
        assertTrue(room.readyToStart());
        send(guest, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.LOBBY, room.round().phase(), "only the owner starts rounds");
        send(host, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, room.round().phase());
        assertFalse(room.isReady(host.slot()), "a round start clears ready");
        assertFalse(room.isReady(guest.slot()));
    }

    @Test
    void setReadyIsRateLimitedIgnoredWhenUnchangedAndRefusedDuringARound() throws Exception {
        Member host = admitOwner();
        Member guest = admitGuests(1).getFirst();
        int before = all(guest, ControlMessage.PlayerReady.class).size();
        send(host, new ControlMessage.SetReady(false)); // already not ready
        send(host, new ControlMessage.SetReady(true));
        send(host, new ControlMessage.SetReady(false)); // too soon after the last change
        assertTrue(room.isReady(host.slot()));
        assertEquals(before + 1, all(guest, ControlMessage.PlayerReady.class).size());
        now += Protocol.READY_MIN_INTERVAL_MILLIS;
        send(host, new ControlMessage.SetReady(false));
        assertFalse(room.isReady(host.slot()));

        send(host, new ControlMessage.SetReadyRule("AUTO"));
        send(host, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, room.round().phase());
        now += Protocol.READY_MIN_INTERVAL_MILLIS;
        send(guest, new ControlMessage.SetReady(false));
        assertTrue(room.isReady(guest.slot()), "ready changes wait for the round to end");
        assertNull(host.connection().closedReason);
        assertNull(guest.connection().closedReason);
    }

    @Test
    void onlyTheOwnerChangesTheRuleAndAutoReadiesEveryone() throws Exception {
        Member host = admitOwner();
        Member guest = admitGuests(1).getFirst();
        send(guest, new ControlMessage.SetReadyRule("AUTO"));
        assertEquals(ReadyRules.DEFAULT_BY_SIZE, room.readyRule());
        send(host, new ControlMessage.SetReadyRule("AUTO"));
        assertEquals(ReadyRules.AUTO, room.readyRule());
        assertEquals(new ControlMessage.ReadyRule("AUTO", false), last(guest, ControlMessage.ReadyRule.class));
        assertEquals(new ControlMessage.ReadyReset(true), last(guest, ControlMessage.ReadyReset.class));
        assertTrue(room.isReady(host.slot()) && room.isReady(guest.slot()), "AUTO readies everyone");

        send(host, new ControlMessage.SetReadyRule("REQUIRED"));
        assertEquals(new ControlMessage.ReadyRule("REQUIRED", true), last(guest, ControlMessage.ReadyRule.class));
        assertTrue(room.isReady(guest.slot()), "players already ready keep it");
        now += Protocol.READY_MIN_INTERVAL_MILLIS;
        send(guest, new ControlMessage.SetReady(false));
        send(host, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.LOBBY, room.round().phase(), "REQUIRED waits for the guest");
    }

    @Test
    void autoReadiesNewcomersAndReappliesReadyAfterARoundStart() throws Exception {
        Member host = admitOwner();
        send(host, new ControlMessage.SetReadyRule("AUTO"));
        Member guest = admitGuests(1).getFirst();
        assertTrue(room.isReady(guest.slot()), "everyone counts as ready on joining");
        assertEquals(new ControlMessage.PlayerReady(guest.slot(), true), last(guest, ControlMessage.PlayerReady.class));
        send(host, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, room.round().phase());
        List<ControlMessage> afterStart = messages(guest.connection());
        int roundStart = indexOf(afterStart, ControlMessage.RoundStart.class);
        assertTrue(roundStart >= 0);
        assertEquals(new ControlMessage.ReadyReset(true), afterStart.get(roundStart + 1),
                "ready is re-applied right after RoundStart clears it");
        assertTrue(room.isReady(host.slot()) && room.isReady(guest.slot()));
    }

    @Test
    void defaultRuleRequiresReadyUpToFivePlayersAndNotFromSix() throws Exception {
        Member host = admitOwner();
        List<Member> guests = admitGuests(4); // five present
        assertFalse(room.readyToStart());
        assertEquals(new ControlMessage.ReadyRule("DEFAULT_BY_SIZE", true),
                last(guests.getLast(), ControlMessage.ReadyRule.class));
        now += Protocol.READY_MIN_INTERVAL_MILLIS;
        send(guests.getFirst(), new ControlMessage.SetReady(true));
        send(guests.get(1), new ControlMessage.SetReady(false)); // unchanged

        Member sixth = admit("Sixth", dir.resolve("sixth"));
        assertEquals(new ControlMessage.ReadyRule("DEFAULT_BY_SIZE", false), last(host, ControlMessage.ReadyRule.class));
        assertEquals(new ControlMessage.ReadyReset(true), last(host, ControlMessage.ReadyReset.class));
        assertTrue(room.isReady(sixth.slot()) && room.isReady(guests.get(2).slot()),
                "a sixth player makes the room AUTO and readies everyone");
        assertTrue(room.readyToStart());

        now += Protocol.READY_MIN_INTERVAL_MILLIS;
        send(guests.get(3), new ControlMessage.SetReady(false)); // one player unreadies in an AUTO room
        assertTrue(room.readyToStart(), "nobody waits with six present");

        room.onDisconnected(sixth.connection());
        assertEquals(new ControlMessage.ReadyRule("DEFAULT_BY_SIZE", true), last(host, ControlMessage.ReadyRule.class));
        assertTrue(room.isReady(guests.get(2).slot()), "an auto-readied player keeps it back at five");
        assertFalse(room.isReady(guests.get(3).slot()), "the player who unreadied stays unready");
        assertFalse(room.readyToStart());
        send(host, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.LOBBY, room.round().phase());
        now += Protocol.READY_MIN_INTERVAL_MILLIS;
        send(guests.get(3), new ControlMessage.SetReady(true));
        send(host, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, room.round().phase());
    }

    @Test
    void aRejoinStartsUnreadyAndANewcomerHearsWhoIsReady() throws Exception {
        Member host = admitOwner();
        Member guest = admit("G", dir.resolve("g"));
        send(guest, new ControlMessage.SetReady(true));
        room.onDisconnected(guest.connection());
        Member again = admit("G", dir.resolve("g"));
        assertFalse(room.isReady(again.slot()));
        send(host, new ControlMessage.SetReady(true));
        Member late = admit("L", dir.resolve("l"));
        assertTrue(all(late, ControlMessage.PlayerReady.class).contains(new ControlMessage.PlayerReady(host.slot(), true)));
        assertFalse(all(late, ControlMessage.PlayerReady.class).contains(new ControlMessage.PlayerReady(again.slot(), true)));
    }

    private static int indexOf(List<ControlMessage> messages, Class<?> type) {
        for (int i = 0; i < messages.size(); i++) if (type.isInstance(messages.get(i))) return i;
        return -1;
    }

    // ── Lobby presence ────────────────────────────────────────────────────

    private static byte[] poses(int stream, int start, int x, int y, int count) {
        byte[] data = new byte[count * GhostFrameCodec.BYTES];
        for (int i = 0; i < count; i++) {
            GhostFrameCodec.encode(new GhostFrame(x + i, y, 1, false, false, false, 2, false), data,
                    i * GhostFrameCodec.BYTES);
        }
        return GhostPackets.encodeLobbyFrames(stream, start, data);
    }

    private static List<GhostPackets.AggregateEntry> lobbyEntries(Member member) {
        List<GhostPackets.AggregateEntry> entries = new ArrayList<>();
        for (byte[] packet : member.connection().binary) {
            if ((packet[0] & 0xFF) == GhostPackets.TYPE_LOBBY_AGGREGATE) {
                entries.addAll(GhostPackets.decodeLobbyAggregate(packet).entries());
            }
        }
        return entries;
    }

    @Test
    void lobbyPosesReachOtherPlayersBetweenRoundsOnly() throws Exception {
        Member host = admitOwner();
        Member guest = admitGuests(1).getFirst();
        room.onBinary(host.connection(), poses(0, 0, 0x100, 0x200, 3));
        room.tick();
        List<GhostPackets.AggregateEntry> heard = lobbyEntries(guest);
        assertEquals(1, heard.size());
        assertEquals(host.slot(), heard.getFirst().playerSlot());
        assertEquals(3, heard.getFirst().frameCount());
        assertTrue(lobbyEntries(host).isEmpty(), "nobody hears their own pose");

        send(host, new ControlMessage.SetReadyRule("AUTO"));
        send(host, new ControlMessage.RoundConfigure(ROUND));
        guest.connection().binary.clear();
        room.onBinary(host.connection(), poses(0, 3, 0x103, 0x200, 3));
        room.tick();
        assertTrue(lobbyEntries(guest).isEmpty(), "no lobby presence during a round");
        assertNull(host.connection().closedReason, "a late lobby pose is not a violation");
        assertTrue(room.round().phase() == HostRoundEngine.Phase.COUNTDOWN);
    }

    @Test
    void aSuppressedFirstLobbyBatchDoesNotTurnLaterPresenceIntoKickViolations() throws Exception {
        Member host = admitOwner();
        Member guest = admitGuests(1).getFirst();
        send(host, new ControlMessage.SetReadyRule("AUTO"));
        send(host, new ControlMessage.RoundConfigure(ROUND));
        assertEquals(HostRoundEngine.Phase.COUNTDOWN, room.round().phase());
        room.onBinary(guest.connection(), poses(1, 0, 0x100, 0x200, 3));
        now += HostRoundEngine.COUNTDOWN_MILLIS + ROUND.windowSeconds() * 1000L
                + HostRoundEngine.FINISH_GRACE_MILLIS + 1;
        room.tick();
        assertEquals(HostRoundEngine.Phase.ROUND_END, room.round().phase());
        for (int start = 3; start < 3 * (GhostStreamValidator.KICK_THRESHOLD + 1); start += 3) {
            room.onBinary(guest.connection(), poses(1, start, 0x100 + start, 0x200, 3));
        }
        room.tick();
        assertNull(guest.connection().closedReason);
        assertEquals(3 * GhostStreamValidator.KICK_THRESHOLD, frames(lobbyEntries(host)));
    }

    @Test
    void lateObservedLobbyStreamsStillRejectNegativeAndOverflowingFrameIndices() throws Exception {
        Member host = admitOwner();
        Member guest = admitGuests(1).getFirst();
        room.onBinary(guest.connection(), poses(1, -1, 0x100, 0x200, 3));
        room.onBinary(guest.connection(), poses(2, Integer.MAX_VALUE, 0x100, 0x200, 3));
        room.tick();
        assertTrue(lobbyEntries(host).isEmpty());
        assertNull(guest.connection().closedReason);
        room.onBinary(guest.connection(), poses(2, 5, 0x100, 0x200, 3));
        room.tick();
        assertEquals(3, frames(lobbyEntries(host)));
    }

    @Test
    void invalidLobbyStreamsAreDroppedAndRepeatedViolationsCloseTheMember() throws Exception {
        Member host = admitOwner();
        Member guest = admitGuests(1).getFirst();
        room.onBinary(guest.connection(), poses(1, 0, 0x100, 0x200, 3));
        room.onBinary(guest.connection(), poses(1, 9, 0x140, 0x200, 3)); // a gap is dropped frames: fine
        room.onBinary(guest.connection(), poses(1, 3, 0x143, 0x200, 3)); // but never backwards
        room.onBinary(guest.connection(), poses(0, 0, 0x100, 0x200, 3)); // nor an older stream
        room.onBinary(guest.connection(), poses(1, 12, 0x900, 0x200, 3)); // teleport inside a stream
        room.onBinary(guest.connection(), new byte[] {(byte) GhostPackets.TYPE_LOBBY_FRAMES, 1, 2});
        room.tick();
        assertEquals(2, lobbyEntries(host).size(), "only the two valid batches were relayed");
        assertNull(guest.connection().closedReason);
        for (int i = 0; i < GhostStreamValidator.KICK_THRESHOLD && guest.connection().closedReason == null; i++) {
            room.onBinary(guest.connection(), poses(0, 0, 0x100, 0x200, 3));
        }
        assertEquals("lobby presence violations", guest.connection().closedReason);
        assertEquals(1, room.playerCount());
    }

    @Test
    void rejectedNewLobbyStreamDoesNotReplaceTheAcceptedStream() throws Exception {
        Member host = admitOwner();
        Member guest = admitGuests(1).getFirst();
        room.onBinary(guest.connection(), poses(0, 0, 0x100, 0x200, 3));

        byte[] invalid = poses(1, 0, 0x200, 0x200, 3);
        byte[] data = GhostPackets.decodeLobbyFrames(invalid).frameData();
        GhostFrameCodec.encode(new GhostFrame(0x900, 0x200, 1,
                false, false, false, 2, false), data, 2 * GhostFrameCodec.BYTES);
        room.onBinary(guest.connection(), GhostPackets.encodeLobbyFrames(1, 0, data));

        room.onBinary(guest.connection(), poses(0, 3, 0x103, 0x200, 3));
        room.tick();
        assertEquals(6, frames(lobbyEntries(host)));
        assertNull(guest.connection().closedReason);
    }

    @Test
    void lobbyPoseRateIsCappedWithoutDesynchronisingTheStream() throws Exception {
        Member host = admitOwner();
        Member guest = admitGuests(1).getFirst();
        int start = 0;
        // The burst allowance, then nothing more until the clock refills it.
        for (int i = 0; i < LobbyPresenceRelay.RATE_BURST_FRAMES / 3; i++) {
            room.onBinary(guest.connection(), poses(0, start, 0x100, 0x200, 3));
            room.tick();
            start += 3;
        }
        room.onBinary(guest.connection(), poses(0, start, 0x100, 0x200, 3)); // over the rate: refused
        start += 3;
        room.tick();
        int relayed = frames(lobbyEntries(host));
        assertEquals(LobbyPresenceRelay.RATE_BURST_FRAMES, relayed);
        now += 1000;
        // The refused frames count as sent, so the stream carries on after them.
        room.onBinary(guest.connection(), poses(0, start, 0x100, 0x200, 3));
        room.tick();
        assertEquals(relayed + 3, frames(lobbyEntries(host)));
        assertNull(guest.connection().closedReason);
    }

    private static int frames(List<GhostPackets.AggregateEntry> entries) {
        return entries.stream().mapToInt(GhostPackets.AggregateEntry::frameCount).sum();
    }

    @Test
    void relayRoomsSendEachRecipientOnlyItsNearestLobbySenders() throws Exception {
        room = room(true);
        Member listener = admitOwner();
        room.onBinary(listener.connection(), poses(0, 0, 0x100, 0x200, 3));
        List<Member> crowd = admitGuests(12);
        for (int i = 0; i < crowd.size(); i++) {
            room.onBinary(crowd.get(i).connection(), poses(0, 0, 0x110 + i * 0x20, 0x200, 3));
        }
        room.tick();
        List<GhostPackets.AggregateEntry> heard = lobbyEntries(listener);
        assertEquals(RelevanceClassifier.NEAR_CAP, heard.size());
        for (GhostPackets.AggregateEntry entry : heard) {
            assertTrue(entry.playerSlot() <= crowd.get(RelevanceClassifier.NEAR_CAP - 1).slot(),
                    "the eight nearest senders: " + entry.playerSlot());
        }
    }

    @Test
    void congestedRecipientsMissLobbyPresence() throws Exception {
        admitOwner();
        FakeHubConnection slow = new FakeHubConnection() {
            @Override public int queuedBytes() { return GhostHub.BP_DEGRADE_BYTES + 1; }
        };
        PlayerIdentity identity = PlayerIdentity.loadOrCreate(dir.resolve("slow"));
        ClientHandshake handshake = new ClientHandshake(identity, "Slow", "", FP, null,
                AuthenticationTranscript.Role.ROOM_DIRECT, () -> AuthenticationTranscript.LOCAL_PLAINTEXT);
        room.onConnected(slow);
        room.onText(slow, ControlCodec.encode(null, handshake.hello()));
        room.onText(slow, ControlCodec.encode(null, handshake.onWelcome(
                (ControlMessage.Welcome) messages(slow).getLast())));
        Member mover = admit("Mover", dir.resolve("mover"));
        room.onBinary(mover.connection(), poses(0, 0, 0x100, 0x200, 3));
        room.tick();
        assertTrue(slow.binary.stream().noneMatch(packet -> (packet[0] & 0xFF) == GhostPackets.TYPE_LOBBY_AGGREGATE));
    }
}
