package com.openggf.game.sonic2.specialstage;

import com.openggf.game.GameServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 5640M/8629S reports jump-dependent halfpipe lag reduction.
 * Actual jumping exercises the fixed live pacing owner, whose only inputs are
 * frame, speed and segment. This proves the absent capability, not a measured
 * native scheduling difference in this particular 240-update window. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2JumpLag {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test
    void currentLimitationJumpingDoesNotChangeTheLiveLagSchedule() throws Exception {
        GameServices.graphics().initHeadless();
        var manager = new Sonic2SpecialStageManager();
        manager.initialize(0);
        int waits = 0;
        while (!manager.getIntro().isComplete() && waits++ < 2000) manager.update();
        assertTrue(manager.getIntro().isComplete(), "must reach the ROM-backed playable half-pipe");
        assertFalse(manager.isFinished());
        var entry = manager.captureRewindSnapshot();
        var running = exercise(manager, false);
        manager.restoreRewindSnapshot(entry);
        var jumping = exercise(manager, true);
        assertEquals(0, running.jumpFrames());
        assertTrue(jumping.jumpFrames() > 20, "input must execute real jump physics");
        assertTrue(running.lagRows().stream().anyMatch(Boolean::booleanValue), "window must contain actual live lag");
        assertEquals(running.lagRows(), jumping.lagRows(),
                "current limitation: native jump-dependent rendering cost is absent from the live pacing model");
    }

    private static Observation exercise(Sonic2SpecialStageManager manager, boolean jump) {
        var lagRows = new ArrayList<Boolean>();
        int jumpFrames = 0;
        for (int i = 0; i < 240; i++) {
            // Frequent fresh A edges allow the next jump after a landing, without
            // changing horizontal path or injecting any observed native state.
            int buttons = jump && (i & 1) == 0 ? 0x40 : 0;
            manager.handleInput(buttons, buttons);
            int before = manager.captureRewindSnapshot().renderFrameCounter;
            manager.update();
            lagRows.add(manager.captureRewindSnapshot().renderFrameCounter == before);
            if (manager.getSonicPlayer().isJumping()) jumpFrames++;
        }
        assertFalse(manager.isFinished(), "both controls must stay inside the same playable segment window");
        return new Observation(List.copyOf(lagRows), jumpFrames);
    }
    private record Observation(List<Boolean> lagRows, int jumpFrames) { }
}
