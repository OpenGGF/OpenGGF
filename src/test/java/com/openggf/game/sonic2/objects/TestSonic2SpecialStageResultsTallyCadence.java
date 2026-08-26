package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SpecialStageResultsTallyCadence {
    private TestObjectServices services;

    @BeforeEach
    void setUp() {
        GameServices.module().createGame(TestEnvironment.currentRom());
        services = (TestObjectServices) new TestObjectServices()
                .withGameModule(GameServices.module())
                .withGameState(new GameStateManager());
    }

    private SpecialStageResultsScreenObjectInstance screen(int p1, int p2, boolean emerald) {
        return new SpecialStageResultsScreenObjectInstance(p1, p2, emerald, 0, 0, services);
    }

    @Test
    void twoPlayerCountdownsDrainInParallel() {
        SpecialStageResultsScreenObjectInstance results = screen(91, 76, false);
        int previousP1 = results.getDisplayedRingCount();
        int previousP2 = results.getDisplayedRingCountP2();
        int tallyFrames = 0;
        for (int frame = 1; frame < 4000; frame++) {
            results.update(frame, null);
            if (results.getDisplayedRingCount() < previousP1
                    || results.getDisplayedRingCountP2() < previousP2) tallyFrames++;
            previousP1 = results.getDisplayedRingCount();
            previousP2 = results.getDisplayedRingCountP2();
            if (previousP1 == 0 && previousP2 == 0 && tallyFrames > 0) break;
        }
        assertEquals(91, tallyFrames,
                "the two ROM countdowns must be serviced by one tally pass each frame");
    }
}
