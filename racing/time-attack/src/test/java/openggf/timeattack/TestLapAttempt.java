package openggf.timeattack;

import com.openggf.mods.run.RaceClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestLapAttempt {
    @Test void highestLapPartitionsGoToFinishDespiteRollback() {
        var a = new TimeAttackAttempt(TimeAttackAttempt.TimingRule.RACE_CLOCK);
        a.onFrame(16, false, -1, new RaceClock(false, 0, 0, RaceClock.Failure.NONE));
        a.onFrame(0, false, -1, new RaceClock(true, 1, 0, RaceClock.Failure.NONE));
        a.onFrame(0, false, -1, new RaceClock(true, 2, 1, RaceClock.Failure.NONE));
        a.onFrame(0, false, -1, new RaceClock(true, 1, 1, RaceClock.Failure.NONE));
        a.onFrame(0, true, -1, new RaceClock(false, 3, 2, RaceClock.Failure.NONE));
        assertEquals(1, a.clockStartFrame());
        assertArrayEquals(new int[]{1, 2}, a.lapFrames());
        assertEquals(3, a.finalTimeFrames());
        assertEquals(a.finalTimeFrames(), java.util.Arrays.stream(a.lapFrames()).sum());
        assertEquals(1, a.bestLapFrames());
    }

    @Test void raceFailureIsTerminalEvenOnCompletion() {
        for (var failure : new RaceClock.Failure[]{RaceClock.Failure.GAME_OVER, RaceClock.Failure.TIME_OVER}) {
            var a = new TimeAttackAttempt(TimeAttackAttempt.TimingRule.RELEASE);
            a.onFrame(0, false, -1);
            a.onFrame(0, true, -1, new RaceClock(false, 0, 0, failure));
            assertEquals(TimeAttackAttempt.Phase.VOID, a.phase());
            assertEquals(failure, a.failure());
            assertEquals(-1, a.finishFrame());
        }
    }
}
