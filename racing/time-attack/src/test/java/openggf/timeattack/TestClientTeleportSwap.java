package openggf.timeattack;

import com.openggf.mods.run.*;
import com.openggf.game.arena.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestClientTeleportSwap {
    @Test void requestQueueEngineResultAndHashedReplay(@TempDir Path dir) throws Exception {
        var runtime = new TimeAttackRuntime(new GhostStore(dir), dir.resolve("id"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s2", 0, 0, "sonic", List.of(), TrackKind.ACT,
                CompetitionRoomSettings.Mode.COMPETITION, MonitorRule.RANDOMISED, true));
        var queued = new ArrayList<RaceEvent>();
        runtime.attachHandle(new RunHandle() {
            public void retry() { fail("competition retry"); }
            public void leave() { }
            public void spectate(boolean active, int dx, int dy) { }
            public boolean isActive() { return true; }
            public void queueRaceEvent(RaceEvent e) { queued.add(e); }
        });
        var requests = new ArrayList<Integer>();
        var outcomes = new ArrayList<String>();
        var recordings = new ArrayList<AttemptInputRecording>();
        runtime.setAttemptListener(new TimeAttackRuntime.AttemptListener() {
            public void onAttemptBegan(int n) { }
            public void onFrameSampled(int n, com.openggf.net.ghost.GhostFrame f) { }
            public void onAttemptVoided(int n) { fail("unexpected void"); }
            public void onAttemptFinished(int n, int time, int start, int finish, byte[] hash, AttemptInputRecording r) { recordings.add(r); }
            public void onTeleportRequest(int n, int frame) { requests.add(frame); }
            public void onTeleportOutcome(long id, int frame, String outcome) { outcomes.add(id + ":" + frame + ":" + outcome); }
        });
        runtime.onLevelReady(new RunLevelStart(runtime.runSpec(), "fp", false, 4096, 512, false));
        var pose = new PlayerPose(100, 200, 1, false, false, 2, false);
        runtime.afterStep(new RunStep(0, 0, false, pose, false, -1, false,
                RaceClock.ACT, RaceSignal.SWAP_REQUEST, RaceEventResult.NONE));
        assertEquals(List.of(0), requests);
        var event = new RaceEvent.SwapApply(42, new SwapTarget(300, 200, 256, 0, false, false, 2, false, 0, 0, 0));
        assertTrue(runtime.queueSwap(event, "Rival"));
        assertEquals(List.of(event), queued);
        runtime.afterStep(new RunStep(1, 0, false, pose, false, -1, false, RaceClock.ACT,
                RaceSignal.NONE, new RaceEventResult(event, RaceEventResult.Outcome.APPLIED)));
        assertEquals("Rival", runtime.competitionHudState().swapNotice().partnerName());
        assertTrue(runtime.queueSwap(new RaceEvent.SwapFallback(0), ""));
        runtime.afterStep(new RunStep(2, 0, false, pose, true, -1, false, RaceClock.ACT,
                RaceSignal.NONE, new RaceEventResult(new RaceEvent.SwapFallback(0), RaceEventResult.Outcome.FALLBACK)));
        assertEquals(List.of("42:1:APPLIED", "0:2:FALLBACK"), outcomes);
        var recorded = recordings.getFirst();
        var replay = AttemptInputRecording.decodeChecked(recorded.encode());
        assertEquals(List.of(new AttemptInputRecording.RecordedEvent(1, event),
                new AttemptInputRecording.RecordedEvent(2, new RaceEvent.SwapFallback(0))), replay.events());
        assertArrayEquals(recorded.sha256(), replay.sha256());
        runtime.requestRetry();
    }
}
