package com.openggf.game.timeattack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTimeAttackAttempt {
    @Test
    void timesFromFirstInputNotSpawn() {
        TimeAttackAttempt a = new TimeAttackAttempt();
        a.onFrame(0, false, -1);          // spawn frame 0, idle
        a.onFrame(0, false, -1);          // frame 1, idle
        a.onFrame(0x08, false, -1);       // frame 2, first input
        a.onFrame(0x08, false, -1);       // frame 3
        assertEquals(TimeAttackAttempt.Phase.RUNNING, a.phase());
        assertEquals(2, a.firstInputFrame());
        assertEquals(1, a.elapsedDisplayFrames()); // frames 2..3 = 1 elapsed
        a.onFrame(0x08, true, -1);        // frame 4, signpost
        assertEquals(TimeAttackAttempt.Phase.FINISHED, a.phase());
        assertEquals(4, a.finishFrame());
        assertEquals(2, a.finalTimeFrames()); // 4 - 2
    }

    @Test
    void recordsSplitsOnNewCheckpointIndexOnly() {
        TimeAttackAttempt a = new TimeAttackAttempt();
        a.onFrame(0x08, false, -1);
        a.onFrame(0x08, false, 1);   // checkpoint 1 at frame 1
        a.onFrame(0x08, false, 1);   // same index — no new split
        a.onFrame(0x08, false, 2);   // checkpoint 2 at frame 3
        assertArrayEquals(new int[] {1, 3}, a.splitFrames());
    }

    @Test
    void staysArmedThroughIdleAndVoidIsTerminal() {
        TimeAttackAttempt a = new TimeAttackAttempt();
        a.onFrame(0, false, -1);
        assertEquals(TimeAttackAttempt.Phase.ARMED, a.phase());
        assertEquals(0, a.elapsedDisplayFrames());
        a.voidAttempt();
        a.onFrame(0x08, true, -1);
        assertEquals(TimeAttackAttempt.Phase.VOID, a.phase());
        assertEquals(-1, a.finishFrame());
    }

    @Test
    void deltasCompareTimedValuesNotSpawnFrames() {
        // Same timed pace, but the attempt idled 60 frames before first input: delta must be 0.
        assertEquals(0, TimeAttackDeltas.deltaAtSplit(new int[] {960}, 60, new int[] {900}, 0, 0));
        assertEquals(60, TimeAttackDeltas.deltaAtSplit(new int[] {900}, 0, new int[] {840}, 0, 0));
        assertEquals(-30, TimeAttackDeltas.deltaAtSplit(new int[] {800, 1700}, 0, new int[] {830, 1730}, 0, 1));
        assertEquals(Integer.MIN_VALUE, TimeAttackDeltas.deltaAtSplit(new int[] {800}, 0, new int[0], 0, 0));
    }
}
