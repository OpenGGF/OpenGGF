package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestClientRaceSession {

    @Test
    void joinDuringRoundEndRestoresThePodiumFromItsSnapshot() {
        var winner = new ControlMessage.StandingsRow(
                0, "Winner", "sonic", 3600, 1, "NONE");
        var session = new ClientRaceSession(() -> now);
        session.applyJoin(new ControlMessage.JoinAccepted("tok", 2,
                new ControlMessage.RoomDescriptor(
                        "LAN", "s3k", 0, 0, "OPEN", null, 8, false),
                new ControlMessage.RoundSnapshot("ROUND_END",
                        new ControlMessage.RoundConfig("s3k", 0, 0, 300, "OPEN", null),
                        now - 310_000, now - 10_000, List.of(winner))));

        assertEquals(ClientRaceSession.Phase.ROUND_END, session.phase());
        assertEquals(List.of(winner), session.podiumTop(3));
    }

    @Test void lateVerdictRefreshesPodium() {
        var session = session();
        session.onControl(new ControlMessage.RoundEnd(List.of(
                new ControlMessage.StandingsRow(0,"A","sonic",100,1,"PENDING"))));
        session.onControl(new ControlMessage.StandingsDelta(List.of()));
        assertTrue(session.podiumTop(3).isEmpty());
    }

    private long now = 500_000;

    private ClientRaceSession session() {
        ClientRaceSession session = new ClientRaceSession(() -> now);
        session.applyJoin(new ControlMessage.JoinAccepted("tok", 1,
                new ControlMessage.RoomDescriptor(
                        "LAN", "s3k", 0, 0, "OPEN", null, 8, false),
                new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of())));
        return session;
    }

    @Test
    void membershipDeltasReplaceBySlotAndFreshJoinClearsTheOldMirror() {
        var session = session();
        var a = new ControlMessage.PlayerInfo(0, "a", "A", "sonic");
        var b = new ControlMessage.PlayerInfo(2, "b", "B", "tails");
        session.onControl(new ControlMessage.RoomState(List.of(b, a)));
        var c = new ControlMessage.PlayerInfo(0, "c", "Replacement", "knuckles");
        session.onControl(new ControlMessage.PlayerUpsert(c));
        assertEquals(List.of(c, b), session.players());
        session.onControl(new ControlMessage.PlayerLeft(2));
        assertEquals(List.of(c), session.players());
        session.applyJoin(new ControlMessage.JoinAccepted("new-token", 1, session.room(),
                new ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, List.of())));
        assertTrue(session.players().isEmpty());
        session.onControl(new ControlMessage.RoomState(List.of(b)));
        assertEquals(List.of(b), session.players());
    }

    @Test
    void membershipUpsertMovesAnIdentityInsteadOfDuplicatingIt() {
        var session = session();
        var original = new ControlMessage.PlayerInfo(0, "identity", "A", "sonic");
        var other = new ControlMessage.PlayerInfo(1, "other", "B", "tails");
        session.onControl(new ControlMessage.RoomState(List.of(original, other)));

        var moved = new ControlMessage.PlayerInfo(2, "identity", "A", "knuckles");
        session.onControl(new ControlMessage.PlayerUpsert(moved));

        assertEquals(List.of(other, moved), session.players());
    }

    @Test
    void clockOffsetIsMedianOfSamples() {
        ClientRaceSession session = session();
        assertEquals(0, session.clockOffsetMillis());
        assertTrue(session.needsMoreClockSamples());
        long[] jitter = {0, -20, 5, 40, -5};
        for (long value : jitter) {
            long t0 = now;
            now += 100;
            session.onControl(new ControlMessage.Pong(t0, t0 + 50 + 1000 + value));
        }
        assertFalse(session.needsMoreClockSamples());
        assertEquals(1000, session.clockOffsetMillis());
        assertEquals(now + 1000, session.hubNowEstimateMillis());
    }

    @Test
    void roundStartDrivesPhasesThroughLocalClock() {
        ClientRaceSession session = session();
        ControlMessage.RoundConfig config =
                new ControlMessage.RoundConfig("s3k", 0, 0, 300, "OPEN", null);
        session.onControl(new ControlMessage.RoundStart(
                config, now + 3000, now + 3000 + 300_000));
        assertEquals(ClientRaceSession.Phase.COUNTDOWN, session.phase());
        assertEquals(3000, session.remainingCountdownMillis());
        assertFalse(session.isWindowOpen());
        now += 3000;
        assertEquals(ClientRaceSession.Phase.RUNNING, session.phase());
        assertTrue(session.isWindowOpen());
        assertEquals(300_000, session.remainingWindowMillis());
        now += 300_001;
        assertEquals(ClientRaceSession.Phase.ROUND_END, session.phase());
        assertFalse(session.isWindowOpen());
        assertEquals(-1, session.remainingWindowMillis());
    }

    @Test
    void midRoundJoinLandsRunningWithStandings() {
        ClientRaceSession session = new ClientRaceSession(() -> now);
        ControlMessage.RoundConfig config =
                new ControlMessage.RoundConfig("s3k", 0, 0, 300, "OPEN", null);
        List<ControlMessage.StandingsRow> rows =
                List.of(new ControlMessage.StandingsRow(0, "A", "sonic", 3600, 1, "NONE"));
        session.applyJoin(new ControlMessage.JoinAccepted("tok", 2,
                new ControlMessage.RoomDescriptor(
                        "LAN", "s3k", 0, 0, "OPEN", null, 8, false),
                new ControlMessage.RoundSnapshot(
                        "RUNNING", config, now - 5000, now + 100_000, rows)));
        assertEquals(ClientRaceSession.Phase.RUNNING, session.phase());
        assertEquals(1, session.standings().size());
        assertEquals(2, session.localSlot());
    }

    @Test
    void standingsRoomStateChatAndKickUpdate() {
        ClientRaceSession session = session();
        session.onControl(new ControlMessage.RoomState(
                List.of(new ControlMessage.PlayerInfo(0, "fp", "A", "sonic", false))));
        assertEquals(1, session.players().size());
        session.onControl(new ControlMessage.StandingsDelta(
                List.of(new ControlMessage.StandingsRow(0, "A", "sonic", 100, 1, "NONE"))));
        assertEquals(100, session.standings().get(0).bestTimeFrames());
        for (int i = 0; i < 10; i++) {
            session.onControl(new ControlMessage.ChatBroadcast(0, "A", "msg" + i));
        }
        assertEquals(8, session.chatLines().size());
        assertEquals("A: msg9", session.chatLines().get(7));
        assertNull(session.kickReason());
        session.onControl(new ControlMessage.Kick("violations"));
        assertEquals("violations", session.kickReason());
    }

    @Test
    void looksAreTrackedPerSlotClearedOnLeaveAndMalformedOnesShowStock() {
        ClientRaceSession session = new ClientRaceSession(() -> 0L);
        session.onControl(new ControlMessage.PlayerLook(2, "00E.200E"));
        assertEquals("00E.200E", session.appearance(2).encode());
        assertTrue(session.appearance(3).isStock());
        session.onControl(new ControlMessage.PlayerLook(3, "not a look"));
        assertTrue(session.appearance(3).isStock());
        session.onControl(new ControlMessage.PlayerLeft(2));
        assertTrue(session.appearance(2).isStock());
    }

    @Test
    void readyStateFollowsTheHostAndClearsAtARoundStart() {
        ClientRaceSession session = new ClientRaceSession(() -> 0L);
        session.applyJoin(new ControlMessage.JoinAccepted("t", 1, null, null));
        session.onControl(new ControlMessage.RoomState(List.of(
                new ControlMessage.PlayerInfo(0, "aa", "A", "sonic"),
                new ControlMessage.PlayerInfo(1, "bb", "B", "tails"))));
        assertEquals("DEFAULT_BY_SIZE", session.readyRule());
        assertTrue(session.readyRequired());
        assertEquals(2, session.playersNotReady().size());
        session.onControl(new ControlMessage.PlayerReady(1, true));
        assertTrue(session.isLocalReady());
        assertEquals(List.of("A"), session.playersNotReady().stream().map(ControlMessage.PlayerInfo::displayName).toList());
        session.onControl(new ControlMessage.ReadyRule("AUTO", false));
        session.onControl(new ControlMessage.ReadyReset(true));
        assertFalse(session.readyRequired());
        assertTrue(session.isReady(0) && session.isReady(1));
        session.onControl(new ControlMessage.RoundStart(
                new ControlMessage.RoundConfig("s2", 0, 0, 60, "OPEN", null), 100, 1000));
        assertFalse(session.isReady(0) || session.isReady(1), "a round start clears ready");
        session.onControl(new ControlMessage.PlayerReady(0, true));
        session.onControl(new ControlMessage.PlayerLeft(0));
        assertFalse(session.isReady(0));
        session.onControl(new ControlMessage.ReadyReset(false));
        assertFalse(session.isLocalReady());
    }
}
