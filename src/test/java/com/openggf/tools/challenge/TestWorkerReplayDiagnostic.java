package com.openggf.tools.challenge;

import com.openggf.game.rewind.CompositeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestWorkerReplayDiagnostic {
    @Test
    void stateOracleIncludesPollReadinessClocksAndNativeCoordinateConvention() {
        var expected = frame(8, 123, 44);
        assertTrue(WorkerReplayDiagnostic.sameNativeFrame(expected, frame(8, 123, 44)));
        assertFalse(WorkerReplayDiagnostic.sameNativeFrame(expected, frame(-1, 123, 44)));
        assertFalse(WorkerReplayDiagnostic.sameNativeFrame(expected, frame(8, 124, 44)));
        assertFalse(WorkerReplayDiagnostic.sameNativeFrame(expected, frame(8, 123, 45)));
        assertEquals(java.util.List.of(), WorkerReplayDiagnostic.gameplayDifferences(
                new CompositeSnapshot(Map.of("owner", new int[] {1, 2})),
                new CompositeSnapshot(Map.of("owner", new int[] {1, 2}))));
        assertFalse(WorkerReplayDiagnostic.gameplayDifferences(
                new CompositeSnapshot(Map.of("owner", new int[] {1, 2})),
                new CompositeSnapshot(Map.of("owner", new int[] {1, 3}))).isEmpty());
        assertEquals("Registry layout differs", WorkerReplayDiagnostic.gameplayDifferences(
                new CompositeSnapshot(Map.of("one", 1)),
                new CompositeSnapshot(Map.of("other", 1))).getFirst());
        assertFalse(WorkerReplayDiagnostic.gameplayDifferences(
                new CompositeSnapshot(Map.of("input", new PrivateOwnerState(1))),
                new CompositeSnapshot(Map.of("input", new PrivateOwnerState(2)))).isEmpty(),
                "A private engine checkpoint record cannot silently hide a mismatch");
    }

    @Test
    void commandAndInputScriptAreBoundedAndDoNotAcceptSnapshotPayloads() {
        var options = WorkerReplayDiagnostic.Options.parse(new String[] {
                "--game", "s1", "--rom", "/sources/s1.gen", "--output", "/captures/rewind"});
        assertEquals("s1", options.game());
        assertEquals(10, options.stableTicks());
        assertEquals(1, WorkerReplayDiagnostic.Options.parse(new String[] {
                "--game", "s1", "--rom", "/sources/s1.gen", "--output", "/captures/rewind",
                "--stable-ticks", "1"}).stableTicks());
        assertThrows(IllegalArgumentException.class, () -> WorkerReplayDiagnostic.Options.parse(new String[] {
                "--game", "s1", "--rom", "/sources/s1.gen", "--output", "/captures/rewind",
                "--stable-ticks", "0"}));
        assertThrows(IllegalArgumentException.class, () -> WorkerReplayDiagnostic.Options.parse(new String[] {
                "--game", "s1", "--rom", "/sources/s1.gen", "--snapshot", "/payload"}));
        assertThrows(IllegalArgumentException.class, () -> WorkerReplayDiagnostic.Options.parse(new String[] {
                "--game", "s1", "--rom", "relative.gen", "--output", "/captures/rewind"}));
        assertEquals(72, WorkerReplayDiagnostic.replayHeld(1));
        assertEquals(8, WorkerReplayDiagnostic.replayHeld(5));
        assertEquals(0, WorkerReplayDiagnostic.replayHeld(21));
        assertEquals(8, WorkerReplayDiagnostic.replayHeld(36));
        assertThrows(IllegalArgumentException.class, () -> WorkerReplayDiagnostic.replayHeld(0));
        assertThrows(IllegalArgumentException.class, () -> WorkerReplayDiagnostic.replayHeld(37));
    }

    private static ChallengeProtocol.Frame frame(int poll, long vInt, long frame) {
        return new ChallengeProtocol.Frame(1, 1, new byte[ChallengeProtocol.RGBA_BYTES],
                new short[1600], "LEVEL", 100, 200, 1, vInt, frame, poll);
    }

    private record PrivateOwnerState(long sequence) { }
}
