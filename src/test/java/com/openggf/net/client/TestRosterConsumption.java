package com.openggf.net.client;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRosterConsumption {
    @Test
    void farPlayersComeFromRosterMinusNearPlaybacks() {
        RemoteGhostRegistry registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "ME", "sonic", false),
                new ControlMessage.PlayerInfo(1, "fp1", "NEAR", "tails", false),
                new ControlMessage.PlayerInfo(2, "fp2", "FAR", "knuckles", false)));

        byte[] frames = new byte[3 * GhostFrameCodec.BYTES];
        for (int i = 0; i < 12; i += 3) {
            registry.onAggregate(new GhostPackets.Aggregate(i, List.of(
                    new GhostPackets.AggregateEntry(1, 1, i, 3, frames.clone()))));
        }
        registry.onRoster(List.of(
                new GhostPackets.RosterEntry(1, 10, 5,
                        GhostPackets.ROSTER_STATUS_RUNNING),
                new GhostPackets.RosterEntry(2, 140, 8,
                        GhostPackets.ROSTER_STATUS_FINISHED)));

        List<RemoteGhostRegistry.FarPlayer> far = registry.farPlayers(0);
        assertEquals(1, far.size());
        assertEquals(2, far.getFirst().slot());
        assertEquals("FAR", far.getFirst().displayName());
        assertEquals(140, far.getFirst().cellX());
        assertEquals(GhostPackets.ROSTER_STATUS_FINISHED, far.getFirst().status());
    }

    @Test
    void stalledPlaybackReturnsToRosterOnceItsAttemptLeavesRunning() {
        RemoteGhostRegistry registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "RUNNING", "sonic", false),
                new ControlMessage.PlayerInfo(1, "fp1", "FINISHED", "tails", false),
                new ControlMessage.PlayerInfo(2, "fp2", "IDLE", "knuckles", false),
                new ControlMessage.PlayerInfo(3, "fp3", "UNLISTED", "sonic", false)));
        int frames = RemoteGhostPlayback.INITIAL_DELAY_FRAMES;
        for (int slot = 0; slot <= 3; slot++) {
            registry.onAggregate(new GhostPackets.Aggregate(0, List.of(entry(slot, 1, 0, frames))));
        }
        // Slot 3 has no roster row, as in a direct room that never sends one.
        registry.onRoster(List.of(
                new GhostPackets.RosterEntry(0, 15, 4, GhostPackets.ROSTER_STATUS_RUNNING),
                new GhostPackets.RosterEntry(1, 15, 4, GhostPackets.ROSTER_STATUS_FINISHED),
                new GhostPackets.RosterEntry(2, 15, 4, GhostPackets.ROSTER_STATUS_IDLE)));

        // Buffered trailing frames and the short extrapolation still play for everyone.
        int playing = frames + RemoteGhostPlayback.EXTRAPOLATE_MAX_FRAMES;
        for (int advance = 0; advance < playing; advance++) {
            assertEquals(List.of(0, 1, 2, 3), slots(registry.advanceAll(-1)), "advance " + advance);
        }
        List<RemoteGhostRegistry.RemoteGhost> stalling = registry.advanceAll(-1);
        assertEquals(List.of(0, 1, 2, 3), slots(stalling));
        assertTrue(stalling.stream().allMatch(ghost -> ghost.state().opacityScale() == 0.5f));

        List<RemoteGhostRegistry.RemoteGhost> next = registry.advanceAll(-1);
        assertEquals(List.of(0, 3), slots(next), "only RUNNING or unlisted stalls wait for the cutoff");
        assertEquals(List.of(1, 2), registry.farPlayers(-1).stream()
                .map(RemoteGhostRegistry.FarPlayer::slot).toList());

        // A retry's frames return before the next roster still says RUNNING.
        registry.onAggregate(new GhostPackets.Aggregate(1, List.of(entry(1, 2, 0, frames))));
        assertEquals(List.of(0, 1, 3), slots(registry.advanceAll(-1)));
    }

    @Test
    void lateTailAfterStallStillPlaysThoughRosterSaysFinished() {
        RemoteGhostRegistry registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(
                new ControlMessage.PlayerInfo(0, "fp0", "ME", "sonic", false),
                new ControlMessage.PlayerInfo(1, "fp1", "FINISHER", "tails", false)));
        int frames = RemoteGhostPlayback.INITIAL_DELAY_FRAMES;
        registry.onAggregate(new GhostPackets.Aggregate(0, List.of(entry(1, 1, 0, frames))));
        // Jitter delays the aggregate carrying the hub's last buffered frames, so
        // the ghost plays dry, extrapolates and stalls while it still shows RUNNING.
        int untilStalled = frames + RemoteGhostPlayback.EXTRAPOLATE_MAX_FRAMES + 1;
        List<RemoteGhostRegistry.RemoteGhost> stalled = List.of();
        for (int advance = 0; advance < untilStalled; advance++) {
            stalled = registry.advanceAll(0);
            assertEquals(List.of(1), slots(stalled), "advance " + advance);
        }
        assertEquals(0.5f, stalled.getFirst().state().opacityScale());

        // One pump then delivers the FINISHED roster and the late same-attempt tail.
        registry.onRoster(List.of(new GhostPackets.RosterEntry(
                1, 15, 4, GhostPackets.ROSTER_STATUS_FINISHED)));
        int tail = 3;
        registry.onAggregate(new GhostPackets.Aggregate(1, List.of(entry(1, 1, frames, tail))));
        for (int index = frames; index < frames + tail; index++) {
            List<RemoteGhostRegistry.RemoteGhost> ghosts = registry.advanceAll(0);
            assertEquals(List.of(1), slots(ghosts), "tail frame " + index + " must still play");
            assertEquals(index, ghosts.getFirst().state().frame().x());
            assertEquals(1.0f, ghosts.getFirst().state().opacityScale());
        }

        // Once the tail has played out and stalls again, the finisher leaves playback.
        for (int advance = 0; advance <= RemoteGhostPlayback.EXTRAPOLATE_MAX_FRAMES; advance++) {
            assertEquals(List.of(1), slots(registry.advanceAll(0)), "advance " + advance);
        }
        assertTrue(registry.advanceAll(0).isEmpty());
        assertEquals(List.of(1), registry.farPlayers(0).stream()
                .map(RemoteGhostRegistry.FarPlayer::slot).toList());
    }

    /** Frame {@code i} has x = {@code i}, so playback order is observable. */
    private static GhostPackets.AggregateEntry entry(int slot, int attemptId, int start, int count) {
        byte[] data = new byte[count * GhostFrameCodec.BYTES];
        for (int i = 0; i < count; i++) {
            GhostFrameCodec.encode(new GhostFrame(start + i, 256, 1,
                    false, false, false, 2, false), data, i * GhostFrameCodec.BYTES);
        }
        return new GhostPackets.AggregateEntry(slot, attemptId, start, count, data);
    }

    private static List<Integer> slots(List<RemoteGhostRegistry.RemoteGhost> ghosts) {
        return ghosts.stream().map(RemoteGhostRegistry.RemoteGhost::slot).sorted().toList();
    }

    @Test
    void resetClearsRosterToo() {
        RemoteGhostRegistry registry = new RemoteGhostRegistry();
        registry.onRoomState(List.of(new ControlMessage.PlayerInfo(
                2, "fp2", "FAR", "knuckles", false)));
        registry.onRoster(List.of(new GhostPackets.RosterEntry(2, 1, 1, 0)));
        assertEquals(1, registry.farPlayers(0).size());
        registry.reset();
        assertTrue(registry.farPlayers(0).isEmpty());
    }
}
