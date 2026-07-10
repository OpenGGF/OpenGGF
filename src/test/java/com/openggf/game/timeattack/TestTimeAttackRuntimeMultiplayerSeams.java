package com.openggf.game.timeattack;

import com.openggf.ghost.GhostFrame;
import com.openggf.sprites.ghost.ActiveGhost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTimeAttackRuntimeMultiplayerSeams {
    private static GhostFrame frame(int x) {
        return new GhostFrame(x, 100, 1, false, false, false, 2, false);
    }

    private record Event(String kind, int ordinal) {
    }

    private static final class RecordingListener implements TimeAttackRuntime.AttemptListener {
        final List<Event> events = new ArrayList<>();
        final List<GhostFrame> sampled = new ArrayList<>();
        byte[] lastInputHash;
        AttemptInputRecording lastRecording;
        int lastTimeFrames = -1;

        @Override
        public void onAttemptBegan(int attemptOrdinal) {
            events.add(new Event("began", attemptOrdinal));
        }

        @Override
        public void onFrameSampled(int attemptOrdinal, GhostFrame frame) {
            events.add(new Event("sampled", attemptOrdinal));
            sampled.add(frame);
        }

        @Override
        public void onAttemptFinished(int attemptOrdinal, int timeFrames,
                                      int firstInputFrame, int finishFrame,
                                      byte[] inputRecordingSha256,
                                      AttemptInputRecording recording) {
            events.add(new Event("finished", attemptOrdinal));
            lastTimeFrames = timeFrames;
            lastInputHash = inputRecordingSha256;
            lastRecording = recording;
        }

        @Override
        public void onAttemptVoided(int attemptOrdinal) {
            events.add(new Event("voided", attemptOrdinal));
        }
    }

    private static TimeAttackRuntime armedRuntime(Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(
                new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest(
                "s3k", 0, 0, "sonic", List.of()));
        return runtime;
    }

    @Test
    void listenerHearsBeganFinishedWithOrdinalsAndHash(@TempDir Path root) {
        TimeAttackRuntime runtime = armedRuntime(root);
        RecordingListener listener = new RecordingListener();
        runtime.setAttemptListener(listener);

        runtime.beginAttemptForTest("0.6:cafe");
        runtime.tickForTest(0, false, false, -1, frame(10));
        runtime.tickForTest(0x08, false, false, -1, frame(11));
        runtime.tickForTest(0x08, false, true, -1, frame(12));
        assertEquals(List.of(new Event("began", 1),
                new Event("sampled", 1), new Event("sampled", 1),
                new Event("sampled", 1), new Event("finished", 1)), listener.events);
        assertEquals(List.of(10, 11, 12),
                listener.sampled.stream().map(GhostFrame::x).toList());
        assertEquals(1, listener.lastTimeFrames);
        assertEquals(32, listener.lastInputHash.length);
    }

    @Test
    void voidCurrentAttemptFiresVoidAndNextAttemptIncrementsOrdinal(@TempDir Path root) {
        TimeAttackRuntime runtime = armedRuntime(root);
        RecordingListener listener = new RecordingListener();
        runtime.setAttemptListener(listener);

        runtime.beginAttemptForTest("0.6:cafe");
        runtime.tickForTest(0x08, false, false, -1, frame(10));
        runtime.voidCurrentAttempt();
        runtime.beginAttemptForTest("0.6:cafe");
        assertEquals(List.of(new Event("began", 1), new Event("sampled", 1),
                new Event("voided", 1), new Event("began", 2)), listener.events);
    }

    @Test
    void voidedAttemptStopsSampling(@TempDir Path root) {
        TimeAttackRuntime runtime = armedRuntime(root);
        RecordingListener listener = new RecordingListener();
        runtime.setAttemptListener(listener);
        runtime.beginAttemptForTest("0.6:cafe");
        runtime.tickForTest(0, false, false, -1, frame(77));
        runtime.voidCurrentAttempt();
        runtime.tickForTest(0, false, false, -1, frame(78));
        assertEquals(1, listener.sampled.size());
    }

    @Test
    void armedAttemptIsActiveAndVoidable(@TempDir Path root) {
        TimeAttackRuntime runtime = armedRuntime(root);
        RecordingListener listener = new RecordingListener();
        runtime.setAttemptListener(listener);
        runtime.beginAttemptForTest("0.6:cafe");
        runtime.tickForTest(0, false, false, -1, frame(10));
        assertFalse(runtime.isAttemptRunning());
        assertTrue(runtime.isAttemptActive());
        runtime.voidCurrentAttempt();
        assertEquals(new Event("voided", 1),
                listener.events.get(listener.events.size() - 1));
        assertFalse(runtime.isAttemptActive());
        runtime.voidCurrentAttempt();
        assertEquals(1, listener.events.stream()
                .filter(event -> event.kind().equals("voided")).count());
    }

    @Test
    void extraGhostSupplierMergesAndCapsAtEight(@TempDir Path root) {
        TimeAttackRuntime runtime = armedRuntime(root);
        List<ActiveGhost> extras = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            extras.add(new ActiveGhost("net:" + i, "sonic", frame(i)));
        }
        runtime.setExtraGhostSupplier(() -> extras);
        runtime.beginAttemptForTest("0.6:cafe");
        runtime.tickForTest(0, false, false, -1, frame(1));
        assertEquals(8, runtime.activeGhostsForTest().size());
    }
}
