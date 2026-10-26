package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageResultsScreen;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * s2-special-stage-tally: 5412M/8376S minimizes surplus special-stage rings.
 * Runs the stock S2 Obj6F countdown shared by that optimization; it does not
 * certify the KiS2 entry, required-ring tables, perfect bonus or seventh-emerald
 * presentation. Exact controlled result counts are deliberately not TAS totals.
 * Oracle: SpecialStage (s2.asm:6537-6788), result writes at 6782-6788;
 * Obj6F_TallyScore (s2.asm:28376-28424), pin 34fc7f130a96db171e1a1c43e28e0bad81cd6ca7.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2SpecialStageTally {
    private TestObjectServices services;

    @BeforeEach
    void setUp() {
        GameServices.module().createGame(TestEnvironment.currentRom());
        services = new TestObjectServices() {
            @Override public <T> T gameService(Class<T> type) {
                return GameServices.module().getGameService(type);
            }
        }.withGameModule(GameServices.module()).withGameState(new GameStateManager());
    }

    @Test
    void surplusRingCostsOnePassOnlyAboveTheEmeraldBonusFloor() throws Exception {
        assertEquals(101, runTally(100));
        assertEquals(102, runTally(101));
        assertEquals(121, runTally(120));
        // Negative control: 1000 native emerald units drain by ten per pass,
        // in parallel with rings. Reducing 41 to 40 cannot shorten this floor.
        assertEquals(101, runTally(40));
        assertEquals(101, runTally(41));
    }

    private int runTally(int rings) throws Exception {
        try (var presentation = com.openggf.tests.reel.S2TestReelPresentation.open()) {
            int beforeScore = services.gameState().getScore();
            var screen = new Sonic2SpecialStageResultsScreen(rings, 0, true, 0, 1, services);
            presentation.attachResults(screen, services);
            int frame = 0;
            while (screen.getState() != 2 && frame < 300) {
                screen.update(++frame, null);
                com.openggf.tests.reel.S2TestReelPresentation.resultsFrame(screen, services);
            }
            assertEquals(2, screen.getState(), "must reach Obj6F_TallyScore through slide and delay");
            assertEquals(rings, screen.getDisplayedRingCount());
            assertEquals(1000, screen.getEmeraldBonus());
            int drains = Math.max(rings, 100);
            for (int pass = 1; pass <= drains; pass++) {
                screen.update(++frame, null);
                com.openggf.tests.reel.S2TestReelPresentation.resultsFrame(screen, services);
                assertEquals(Math.max(0, rings - pass), screen.getDisplayedRingCount());
                assertEquals(Math.max(0, 1000 - pass * 10), screen.getEmeraldBonus());
                assertEquals(2, screen.getState(), "last draining pass must not advance routine");
            }
            screen.update(++frame, null);
            com.openggf.tests.reel.S2TestReelPresentation.resultsFrame(screen, services);
            assertEquals(3, screen.getState(), "only the already-empty pass enters the wait");
            assertEquals(rings * 100 + 10000, services.gameState().getScore() - beforeScore);
            return drains + 1;
        }
    }
}
