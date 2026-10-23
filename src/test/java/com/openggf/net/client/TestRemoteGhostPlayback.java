package com.openggf.net.client;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestRemoteGhostPlayback {

    @Test void expiredNearPlaybackReturnsToFreshCoarseRosterAndCanResume() {
        var registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(new ControlMessage.PlayerInfo(0,"fp","A","sonic",false)));
        registry.onAggregate(new GhostPackets.Aggregate(0,List.of(entry(1,0,9))));
        assertFalse(registry.advanceAll(-1).isEmpty());
        registry.onRoster(List.of(new GhostPackets.RosterEntry(0,200,3,1)));
        for(int i=0;i<600;i++) registry.advanceAll(-1);
        assertEquals(1,registry.farPlayers(-1).size());
        assertTrue(registry.advanceAll(-1).isEmpty());
        registry.onAggregate(new GhostPackets.Aggregate(601,List.of(entry(1,600,9))));
        assertFalse(registry.advanceAll(-1).isEmpty());
        assertTrue(registry.farPlayers(-1).isEmpty());
    }

    @Test
    void membershipMetadataChangesPreservePlaybackButIdentityReplacementAndLeaveClearIt() {
        var registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(new ControlMessage.PlayerInfo(0, "original", "A", "sonic")));
        registry.onAggregate(new GhostPackets.Aggregate(0, List.of(entry(1, 0, 9))));
        assertFalse(registry.advanceAll(-1).isEmpty());
        registry.onPlayerUpsert(new ControlMessage.PlayerInfo(0, "original", "A", "tails"));
        assertEquals("tails", registry.advanceAll(-1).getFirst().character());
        registry.onRoster(List.of(new GhostPackets.RosterEntry(0, 200, 3, 1)));
        registry.onPlayerUpsert(new ControlMessage.PlayerInfo(0, "replacement", "B", "knuckles"));
        assertTrue(registry.advanceAll(-1).isEmpty());
        assertTrue(registry.farPlayers(-1).isEmpty());
        registry.onAggregate(new GhostPackets.Aggregate(1, List.of(entry(1, 0, 9))));
        assertEquals("B", registry.advanceAll(-1).getFirst().displayName());
        registry.onPlayerLeft(0);
        registry.onRoster(List.of(new GhostPackets.RosterEntry(0, 200, 3, 1)));
        assertTrue(registry.advanceAll(-1).isEmpty());
        assertTrue(registry.farPlayers(-1).isEmpty());
    }

    @Test
    void freshMembershipSnapshotDoesNotReusePreviousConnectionPlayback() {
        var registry = new RemoteGhostRegistry();
        var player = new ControlMessage.PlayerInfo(0, "same", "A", "sonic");
        registry.onRoomState(List.of(player));
        registry.onAggregate(new GhostPackets.Aggregate(0, List.of(entry(9, 0, 9))));
        assertFalse(registry.advanceAll(-1).isEmpty());
        registry.onRoomState(List.of(player));
        assertTrue(registry.advanceAll(-1).isEmpty());
        registry.onAggregate(new GhostPackets.Aggregate(1, List.of(entry(1, 0, 9))));
        assertFalse(registry.advanceAll(-1).isEmpty());
    }

    @Test
    void membershipUpsertMovesAnIdentityAndClearsItsOldSlotPlayback() {
        var registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(
                new ControlMessage.PlayerInfo(0, "identity", "A", "sonic"),
                new ControlMessage.PlayerInfo(1, "other", "B", "tails")));
        registry.onAggregate(new GhostPackets.Aggregate(0, List.of(entryForSlot(0, 1, 0, 9))));
        assertFalse(registry.advanceAll(-1).isEmpty());

        registry.onPlayerUpsert(new ControlMessage.PlayerInfo(2, "identity", "Moved", "knuckles"));
        assertTrue(registry.advanceAll(-1).isEmpty());
        registry.onAggregate(new GhostPackets.Aggregate(1, List.of(entryForSlot(0, 1, 9, 9))));
        assertTrue(registry.advanceAll(-1).isEmpty(), "retired slot must ignore later aggregates");
        registry.onAggregate(new GhostPackets.Aggregate(2, List.of(entryForSlot(2, 1, 0, 9))));
        assertEquals("Moved", registry.advanceAll(-1).getFirst().displayName());
    }

    private static byte[] frames(int startIndex, int count) {
        byte[] data = new byte[count * GhostFrameCodec.BYTES];
        for (int i = 0; i < count; i++) {
            GhostFrameCodec.encode(new GhostFrame(startIndex + i, 256, 1,
                    false, false, false, 2, false), data, i * GhostFrameCodec.BYTES);
        }
        return data;
    }

    private static GhostPackets.AggregateEntry entry(
            int attemptId, int startIndex, int count) {
        return entryForSlot(0, attemptId, startIndex, count);
    }

    private static GhostPackets.AggregateEntry entryForSlot(
            int slot, int attemptId, int startIndex, int count) {
        return new GhostPackets.AggregateEntry(
                slot, attemptId, startIndex, count, frames(startIndex, count));
    }

    private static RemoteGhostPlayback fed(int upTo) {
        RemoteGhostPlayback playback = new RemoteGhostPlayback();
        for (int i = 0; i < upTo; i += 3) {
            playback.onEntry(entry(1, i, Math.min(3, upTo - i)));
        }
        return playback;
    }

    @Test
    void waitsForInitialBufferThenPlaysFromFrameZero() {
        RemoteGhostPlayback playback = fed(RemoteGhostPlayback.INITIAL_DELAY_FRAMES - 3);
        assertTrue(playback.advance().isEmpty());
        playback.onEntry(entry(1, RemoteGhostPlayback.INITIAL_DELAY_FRAMES - 3, 3));
        RemoteGhostPlayback.RenderState first = playback.advance().orElseThrow();
        assertEquals(0, first.frame().x());
        assertEquals(1.0f, first.opacityScale());
        assertEquals(1, playback.advance().orElseThrow().frame().x());
    }

    @Test
    void catchesUpAtDoubleSpeedOnBacklog() {
        RemoteGhostPlayback playback = fed(9);
        playback.advance();
        for (int i = 9; i < 60; i += 3) {
            playback.onEntry(entry(1, i, 3));
        }
        RemoteGhostPlayback.RenderState state = playback.advance().orElseThrow();
        assertEquals(2, state.frame().x());
        assertFalse(state.snapped());
    }

    @Test
    void snapsOnHugeBacklog() {
        RemoteGhostPlayback playback = fed(9);
        playback.advance();
        for (int i = 9; i < 120; i += 3) {
            playback.onEntry(entry(1, i, 3));
        }
        RemoteGhostPlayback.RenderState state = playback.advance().orElseThrow();
        assertTrue(state.snapped());
        assertEquals(119 - playback.delayFrames(), state.frame().x());
    }

    @Test
    void extrapolatesThenFreezesOnStall() {
        RemoteGhostPlayback playback = fed(RemoteGhostPlayback.INITIAL_DELAY_FRAMES);
        for (int i = 0; i < RemoteGhostPlayback.INITIAL_DELAY_FRAMES; i++) {
            playback.advance();
        }
        int lastRealX = RemoteGhostPlayback.INITIAL_DELAY_FRAMES - 1;
        for (int step = 1; step <= RemoteGhostPlayback.EXTRAPOLATE_MAX_FRAMES; step++) {
            RemoteGhostPlayback.RenderState state = playback.advance().orElseThrow();
            assertEquals(lastRealX + step, state.frame().x());
            assertEquals(1.0f, state.opacityScale());
        }
        assertEquals(0.5f, playback.advance().orElseThrow().opacityScale());
        assertTrue(playback.isStalled());
        assertTrue(playback.delayFrames() > RemoteGhostPlayback.INITIAL_DELAY_FRAMES);
    }

    @Test
    void newAttemptResetsBufferAndCursor() {
        RemoteGhostPlayback playback = fed(30);
        playback.advance();
        playback.onEntry(entry(2, 0, 3));
        assertTrue(playback.advance().isEmpty());
        playback.onEntry(entry(2, 3, 3));
        playback.onEntry(entry(2, 6, 3));
        assertEquals(0, playback.advance().orElseThrow().frame().x());
        playback.onEntry(entry(1, 30, 3));
        assertEquals(1, playback.advance().orElseThrow().frame().x());
    }

    @Test
    void attemptSwitchAfterDryEndRestoresInitialDelay() {
        RemoteGhostPlayback playback = fed(RemoteGhostPlayback.INITIAL_DELAY_FRAMES);
        for (int i = 0; i < RemoteGhostPlayback.INITIAL_DELAY_FRAMES; i++) {
            playback.advance();
        }
        // The finished attempt stops streaming; every dry frame grows the delay.
        for (int i = 0; i < 20; i++) {
            playback.advance();
        }
        assertEquals(RemoteGhostPlayback.MAX_DELAY_FRAMES, playback.delayFrames());

        playback.onEntry(entry(2, 0, RemoteGhostPlayback.INITIAL_DELAY_FRAMES));
        assertEquals(RemoteGhostPlayback.INITIAL_DELAY_FRAMES, playback.delayFrames());
        RemoteGhostPlayback.RenderState first = playback.advance().orElseThrow(
                () -> new AssertionError("a retry starts after the initial buffer, not 30 frames late"));
        assertEquals(0, first.frame().x());
        assertEquals(1.0f, first.opacityScale());
    }

    @Test
    void attemptSwitchRestartsCleanStreak() {
        RemoteGhostPlayback playback = new RemoteGhostPlayback();
        int next = 0;
        int clean = 0;
        while (clean < RemoteGhostPlayback.DELAY_SHRINK_CLEAN_STREAK - 1) {
            playback.onEntry(entry(1, next++, 1));
            if (playback.advance().isPresent()) {
                clean++;
            }
        }
        assertEquals(RemoteGhostPlayback.INITIAL_DELAY_FRAMES, playback.delayFrames());

        playback.onEntry(entry(2, 0, RemoteGhostPlayback.INITIAL_DELAY_FRAMES));
        playback.advance().orElseThrow();
        assertEquals(RemoteGhostPlayback.INITIAL_DELAY_FRAMES, playback.delayFrames(),
                "the old attempt's clean streak must not shrink the new attempt's delay");
    }

    @Test
    void registryRoutesAdvancesAndExcludesLocalSlot() {
        RemoteGhostRegistry registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "A", "sonic", false),
                new ControlMessage.PlayerInfo(1, "fp1", "B", "tails", false)));
        for (int i = 0; i < 12; i += 3) {
            registry.onAggregate(new GhostPackets.Aggregate(i, List.of(
                    new GhostPackets.AggregateEntry(0, 1, i, 3, frames(i, 3)),
                    new GhostPackets.AggregateEntry(1, 1, i, 3, frames(i, 3)))));
        }
        List<RemoteGhostRegistry.RemoteGhost> ghosts = registry.advanceAll(1);
        assertEquals(1, ghosts.size());
        assertEquals("A", ghosts.get(0).displayName());
        assertEquals("sonic", ghosts.get(0).character());
        registry.onRoomState(List.of(
                new ControlMessage.PlayerInfo(1, "fp1", "B", "tails", false)));
        assertTrue(registry.advanceAll(1).isEmpty());
    }

    @Test
    void resetDropsPlaybacksSoNextRoundAttemptIdsRestart() {
        RemoteGhostRegistry registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "A", "sonic", false)));
        for (int i = 0; i < 12; i += 3) {
            registry.onAggregate(new GhostPackets.Aggregate(i, List.of(
                    new GhostPackets.AggregateEntry(0, 5, i, 3, frames(i, 3)))));
        }
        assertFalse(registry.advanceAll(-1).isEmpty());
        registry.reset();
        for (int i = 0; i < 12; i += 3) {
            registry.onAggregate(new GhostPackets.Aggregate(i, List.of(
                    new GhostPackets.AggregateEntry(0, 1, i, 3, frames(i, 3)))));
        }
        assertEquals("A", registry.advanceAll(-1).get(0).displayName());
    }

    @Test
    void sparseFutureFramesCannotGrowPlaybackBufferWithoutBound() throws Exception {
        RemoteGhostPlayback playback = new RemoteGhostPlayback();
        for (int i = 0; i < 500; i++) {
            playback.onEntry(entry(1, i * 31, 1));
            playback.advance();
        }
        var field = RemoteGhostPlayback.class.getDeclaredField("frames");
        field.setAccessible(true);
        assertTrue(((Map<?, ?>) field.get(playback)).size() <= 128);
    }

    @Test
    void aggregateCannotAllocatePlaybackForUnrosteredSlot() {
        RemoteGhostRegistry registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of());
        for (int i = 0; i < 12; i += 3) {
            registry.onAggregate(new GhostPackets.Aggregate(i,
                    List.of(new GhostPackets.AggregateEntry(
                            99, 1, i, 3, frames(i, 3)))));
        }
        assertTrue(registry.advanceAll(-1).isEmpty());
    }
}
