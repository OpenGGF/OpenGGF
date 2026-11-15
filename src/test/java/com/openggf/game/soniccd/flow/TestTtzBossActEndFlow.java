package com.openggf.game.soniccd.flow;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.program.CdStageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tidal Tempest act 3's end on the campaign route, by {@code TestPpzBossActEndFlow}'s pattern: a New Game, the eight
 * act clears before it reported through the flow as the results object reports them (Palmtree Panic and Collision
 * Chaos acts 1-3, Tidal Tempest acts 1-2), then on the act 3 stage the player breaks the flower capsule ($15 at ($E30,
 * $548) in the act 3 layout). Its {@code StartResults} spawns the results object, whose {@code ObjResults_NextLevel}
 * reports the act's end and IPX's {@code RunBossLevel} loads the next zone's first act (IPX.asm:606-644): Quartz
 * Quadrant act 1 present, R51A. Native has no campaign run through act 3; this is the flow's own check.
 */
@EnabledOnOs(OS.LINUX)
class TestTtzBossActEndFlow {
    @Test
    void breakingTheCapsuleEndsTheZoneAndLoadsQuartzQuadrantActOne() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            CdStagePayload[] next = {CdStagePayload.R12A, null, CdStagePayload.R31A, CdStagePayload.R32A, null,
                    CdStagePayload.R41A, CdStagePayload.R42A, null};
            for (CdStagePayload n : next) {
                var state = GameServices.gameState();
                h.scenes.stageEnded(CdStageResult.cleared(state.getLives(), CdStageResult.TIME_PRESENT,
                        h.timeTravel().goodFuture(), h.timeTravel().timeStones()), GameServices.level());
                if (n != null) h.until(() -> h.row().payload() == n, 900, 0);
                else h.until(() -> h.row().payload() != null && h.row().payload().stage().actNumber() == 3, 900, 0);
            }
            h.until(() -> h.row().payload() == CdStagePayload.R43C || h.row().payload() == CdStagePayload.R43D, 900, 0);
            CdStagePayload act3 = h.row().payload();
            h.until(() -> !h.row().ctrlLocked(), 900, 0);
            // Beside the capsule, on its floor, then a jump into it.
            h.place(0xE18, 0x558, 0xD97, 0x4C0);
            h.idle(40);
            for (int i = 0; i < 12; i++) h.step(0, CdFlowHarness.A, false);
            int frames = h.until(() -> h.row().payload() == CdStagePayload.R51A, 3000, 0);
            System.out.printf("TTZBOSS act end: %s capsule jump to R51A in %d frames, route %s%n", act3, frames,
                    h.scenes.route());
            assertTrue(h.scenes.route().stream().anyMatch(r -> r.startsWith("stage ended")), h.scenes.route().toString());
            assertEquals(CdStagePayload.R51A, h.row().payload(), "RunBossLevel: the next zone's act 1 present");
            assertEquals(3, h.scenes.flow().zone(), "zone byte after the boss act");
            assertEquals(0, h.scenes.flow().act(), "act byte after the boss act");
        }
    }
}
