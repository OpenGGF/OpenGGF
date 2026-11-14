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
 * Wacky Workbench act 3's end on the campaign route, by the flow's own pattern ({@link TestPpzBossActEndFlow}): a New
 * Game, every act before it cleared through the flow as the results object reports them, then on the act 3 stage the
 * player breaks the flower capsule (CapsuleObject $15 at ($E38, $260), r6/stage_object_maps_3.asm). The capsule's
 * {@code StartResults} spawns the results object; its {@code ObjResults_NextLevel} reports the act's end
 * ({@code CdStageExit.actCleared}) and IPX's {@code RunBossLevel} goes on to the next zone's first act (IPX.asm:606-644):
 * Stardust Speedway act 1 present, R71A.
 */
@EnabledOnOs(OS.LINUX)
class TestWwzBossActEndFlow {
    @Test
    void breakingTheCapsuleEndsTheZoneAndLoadsStardustSpeedwayActOne() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            for (int guard = 0; guard < 20; guard++) {
                CdStagePayload now = h.row().payload();
                if (now == CdStagePayload.R63C || now == CdStagePayload.R63D) break;
                var state = GameServices.gameState();
                h.scenes.stageEnded(CdStageResult.cleared(state.getLives(), CdStageResult.TIME_PRESENT,
                        h.timeTravel().goodFuture(), h.timeTravel().timeStones()), GameServices.level());
                h.until(() -> h.row().payload() != null && h.row().payload() != now, 1800, 0);
            }
            CdStagePayload act3 = h.row().payload();
            assertTrue(act3 == CdStagePayload.R63C || act3 == CdStagePayload.R63D, "reached act 3: " + act3);
            h.until(() -> !h.row().ctrlLocked(), 900, 0);
            // Left of the capsule, dropped onto its floor, then a run and a jump into it.
            h.place(0xE00, 0x200, 0xD97, 0x1A0);
            h.idle(60);
            for (int i = 0; i < 20; i++) h.step(CdFlowHarness.RIGHT, i >= 8 ? CdFlowHarness.A : 0, false);
            int frames = h.until(() -> h.row().payload() == CdStagePayload.R71A, 3000, 0);
            System.out.printf("WWZBOSS act end: %s capsule jump to R71A in %d frames, route %s%n", act3, frames,
                    h.scenes.route());
            assertTrue(h.scenes.route().stream().anyMatch(r -> r.startsWith("stage ended")), h.scenes.route().toString());
            assertEquals(CdStagePayload.R71A, h.row().payload(), "RunBossLevel: the next zone's act 1 present");
            assertEquals(5, h.scenes.flow().zone(), "zone byte after the boss act");
            assertEquals(0, h.scenes.flow().act(), "act byte after the boss act");
        }
    }
}
