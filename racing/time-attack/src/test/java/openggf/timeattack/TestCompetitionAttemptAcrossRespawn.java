package openggf.timeattack;

import com.openggf.mods.run.LevelStartCause;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCompetitionAttemptAcrossRespawn {
    @Test void runtimeKeepsAttemptAndRecordingAcrossCheckpointReload(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        var runtime = new TimeAttackRuntime(new GhostStore(dir), dir.resolve("id"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s2", 0, 0, "sonic", java.util.List.of(),
                com.openggf.mods.run.TrackKind.ACT, CompetitionRoomSettings.Mode.COMPETITION,
                com.openggf.mods.run.MonitorRule.OFF, false));
        var lifecycle = new java.util.ArrayList<String>();
        runtime.setAttemptListener(new TimeAttackRuntime.AttemptListener() {
            public void onAttemptBegan(int n) { lifecycle.add("begin"); }
            public void onFrameSampled(int n, com.openggf.net.ghost.GhostFrame f) { }
            public void onAttemptVoided(int n) { lifecycle.add("void"); }
            public void onAttemptFinished(int n, int time, int start, int finish, byte[] hash, AttemptInputRecording r) {
                assertEquals(0, start); assertEquals(3, time); assertEquals(4, r.frameCount());
                assertArrayEquals(new int[]{1, 3}, runtime.splitFrames()); lifecycle.add("finish");
            }
        });
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 4096, 512, false));
        var pose = new com.openggf.mods.run.PlayerPose(100, 100, 0, false, false, 2, false);
        runtime.afterStep(new com.openggf.mods.run.RunStep(0, 0, false, pose, false, -1, false));
        runtime.afterStep(new com.openggf.mods.run.RunStep(1, 0, false, pose, false, 1, false));
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 4096, 512,
                LevelStartCause.CHECKPOINT_RESPAWN, com.openggf.mods.run.TrackGeometry.ACT));
        runtime.afterStep(new com.openggf.mods.run.RunStep(0, 0, false, pose, false, 1, false));
        runtime.afterStep(new com.openggf.mods.run.RunStep(1, 0, false, pose, true, 2, false));
        assertEquals(java.util.List.of("begin", "finish"), lifecycle);
        assertFalse(runtime.hudState().newBest());
    }

    @Test void checkpointContinuationNeverRefundsClockOrSplits() {
        var a = new TimeAttackAttempt(TimeAttackAttempt.TimingRule.RELEASE);
        a.onFrame(0, false, -1);
        a.onFrame(0, false, 1);
        assertTrue(a.continuesAcross(LevelStartCause.CHECKPOINT_RESPAWN));
        a.onFrame(0, false, 1);
        a.onFrame(0, true, 2);
        assertEquals(0, a.clockStartFrame());
        assertEquals(3, a.finalTimeFrames());
        assertArrayEquals(new int[]{1,3}, a.splitFrames());
        assertFalse(a.continuesAcross(LevelStartCause.CHECKPOINT_RESPAWN));
        assertFalse(new TimeAttackAttempt().continuesAcross(LevelStartCause.CHECKPOINT_RESPAWN));
    }
}
