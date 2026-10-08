package com.openggf.tools.challenge;

import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** A real production opening/renderer/producer check; this does not certify a route. */
@RequiresRom(SonicGame.SONIC_1)
class TestWorkerGameSessionRom {
    @Test
    void nativeOpeningPlaysThroughTitleWithRealMovingPixelsAndChangingStereoPcm() throws Exception {
        // Shared headless fixtures leave a reusable logic-only world. A worker
        // starts in a new JVM; retire that test fixture to reproduce its boundary.
        com.openggf.game.session.SessionManager.clear();
        var rom = RomTestUtils.ensureSonic1RomAvailable().toPath().toAbsolutePath();
        try (WorkerGameSession session = WorkerGameSession.open("s1", rom)) {
            var ready = session.initial(1, 0);
            assertThrows(IllegalStateException.class, () -> WorkerGameSession.open("s1", rom),
                    "A second in-process world must not destroy the current worker world");
            var start = session.initial(1, 0);
            assertEquals(ready.vInt(), start.vInt(), "Preparing/starting must not advance native clocks");
            assertEquals(ready.levelFrame(), start.levelFrame());
            assertEquals(0, ready.pcm().length);
            assertArrayEquals(ready.rgba(), start.rgba(), "Repeated snapshot must retain the same GPU frame");
            ChallengeProtocol.Frame last = ready;
            long samples = 0;
            double sum = 0, squared = 0;
            int changingPackets = 0;
            boolean titleObserved = false;
            boolean replayChecked = false;
            for (int tick = 1; tick <= 420; tick++) {
                last = session.step(1, tick, 8); // One common held RIGHT signal, no positioned start.
                titleObserved |= last.mode().equals("TITLE_CARD");
                assertEquals(1600, last.pcm().length, "One complete 48kHz stereo presentation packet");
                boolean changing = false;
                for (int sample = 2; sample < last.pcm().length; sample++) {
                    changing |= last.pcm()[sample] != last.pcm()[sample - 2];
                }
                if (changing) changingPackets++;
                for (short sample : last.pcm()) {
                    sum += sample;
                    squared += (double) sample * sample;
                    samples++;
                }
                if (!replayChecked && last.levelFrame() >= 20 && session.diagnosticBoundaryStable()) {
                    verifyOwnCheckpointReplayAndTimelineBoundary(session, tick, last);
                    replayChecked = true;
                }
            }
            assertTrue(titleObserved, "Normal production title was presented");
            assertEquals("LEVEL", last.mode());
            assertTrue(last.levelFrame() > ready.levelFrame());
            assertTrue(last.x() > ready.x() + 32, "Production held input must move the player");
            assertFalse(java.util.Arrays.equals(ready.rgba(), last.rgba()), "Actual native frame must change");
            HashSet<Integer> colors = new HashSet<>();
            for (int i = 0; i < last.rgba().length; i += 4) {
                colors.add((last.rgba()[i] & 255) << 16
                        | (last.rgba()[i + 1] & 255) << 8 | last.rgba()[i + 2] & 255);
            }
            assertTrue(colors.size() > 16, "Real ROM tiles/sprites require a varied GPU palette");
            assertTrue(squared / samples - Math.pow(sum / samples, 2) > 100,
                    "PCM must have AC variance; nonzero DC alone is not music");
            assertTrue(changingPackets > 30,
                    "Multiple real packets must vary over time in a channel; silence/DC transitions are insufficient");
            assertTrue(last.polledMask() == 8 || last.polledMask() == -1);
            assertTrue(replayChecked, "A stable native-controlled opening checkpoint was exercised");
        }
        assertNull(com.openggf.game.session.SessionManager.getCurrentGameplayMode());
        assertNull(com.openggf.game.session.SessionManager.getCurrentWorldSession());
    }

    private static void verifyOwnCheckpointReplayAndTimelineBoundary(WorkerGameSession session,
                                                                    int checkpointTick,
                                                                    ChallengeProtocol.Frame checkpointFrame) {
        // The transport exposes no restore payload: this diagnostic uses
        // only the current production owners' own process-local snapshots.
        var checkpoint = session.captureDiagnosticCheckpoint();
        var expected = new java.util.ArrayList<ChallengeProtocol.Frame>();
        var states = new java.util.ArrayList<com.openggf.game.rewind.CompositeSnapshot>();
        for (int tick = 1; tick <= 12; tick++) {
            expected.add(session.step(1, checkpointTick + tick, WorkerReplayDiagnostic.replayHeld(tick)));
            states.add(session.captureDiagnosticGameplay());
        }
        session.restoreDiagnosticCheckpoint(checkpoint);
        for (int tick = 1; tick <= 12; tick++) {
            var replay = session.step(1, checkpointTick + tick, WorkerReplayDiagnostic.replayHeld(tick));
            var forward = expected.get(tick - 1);
            String at = " at checkpoint tick=" + checkpointTick + " levelFrame=" + checkpointFrame.levelFrame()
                    + " vInt=" + checkpointFrame.vInt() + " x=" + checkpointFrame.x() + " y=" + checkpointFrame.y();
            assertTrue(WorkerReplayDiagnostic.sameNativeFrame(forward, replay), "Native replay tick " + tick + at);
            var differences = WorkerReplayDiagnostic.gameplayDifferences(
                    states.get(tick - 1), session.captureDiagnosticGameplay());
            assertEquals(java.util.List.of(), differences, "Owners replay tick " + tick + at);
            assertArrayEquals(forward.rgba(), replay.rgba(), "GPU replay tick " + tick + at);
            assertArrayEquals(forward.pcm(), replay.pcm(), "Pre-focus PCM replay tick " + tick);
        }
        session.restoreDiagnosticCheckpoint(checkpoint);
        // A native pause is a new diagnostic timeline; input and transport
        // cannot silently restore a checkpoint across that boundary.
        session.step(1, checkpointTick + 13, 0);
        session.step(1, checkpointTick + 14, 128);
        assertThrows(IllegalStateException.class, () -> session.restoreDiagnosticCheckpoint(checkpoint));
        session.step(1, checkpointTick + 15, 0);
        session.step(1, checkpointTick + 16, 128);
        assertThrows(IllegalArgumentException.class, () -> session.restoreDiagnosticCheckpoint(checkpoint));
    }
}
