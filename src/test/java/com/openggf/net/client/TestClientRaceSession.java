package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestClientRaceSession {
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
                List.of(new ControlMessage.StandingsRow(0, "A", "sonic", 3600, 1));
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
                List.of(new ControlMessage.StandingsRow(0, "A", "sonic", 100, 1))));
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
}
