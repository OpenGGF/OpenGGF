package com.openggf.net.client;

import com.openggf.game.ghost.GhostFrame;
import com.openggf.game.ghost.GhostFrameCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestRemoteGhostPlayback {
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
        return new GhostPackets.AggregateEntry(
                0, attemptId, startIndex, count, frames(startIndex, count));
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
}
