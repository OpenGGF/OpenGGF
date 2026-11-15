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
 * Palmtree Panic act 3's end on the campaign route, by the flow's own pattern ({@link TestCdGameFlowNative}): a New
 * Game, the two act clears reported through the flow as the results object reports them, then on the act 3 stage
 * the player breaks the flower capsule. The capsule's {@code StartResults} spawns the results object; its
 * {@code ObjResults_NextLevel} reports the act's end ({@code CdStageExit.actCleared}) and IPX's
 * {@code RunBossLevel} goes on to the next zone's first act (IPX.asm:606-644): Collision Chaos act 1 present, R31A.
 * Native has no campaign run through act 3 yet; the R13C movie's own act end is {@code TestPpzBossNative}.
 */
@EnabledOnOs(OS.LINUX)
class TestPpzBossActEndFlow {
    @Test
    void breakingTheCapsuleEndsTheZoneAndLoadsCollisionChaosActOne() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            for (CdStagePayload next : new CdStagePayload[] {CdStagePayload.R12A, null}) {
                var state = GameServices.gameState();
                h.scenes.stageEnded(CdStageResult.cleared(state.getLives(), CdStageResult.TIME_PRESENT,
                        h.timeTravel().goodFuture(), h.timeTravel().timeStones()), GameServices.level());
                if (next != null) h.until(() -> h.row().payload() == next, 900, 0);
            }
            h.until(() -> h.row().payload() == CdStagePayload.R13C || h.row().payload() == CdStagePayload.R13D, 900, 0);
            CdStagePayload act3 = h.row().payload();
            h.until(() -> !h.row().ctrlLocked(), 900, 0);
            // Beside the capsule (ObjCapsule at $E48,$3A0 in the act 3 layout), on its floor, then a jump into it.
            h.place(0xE30, 0x3B0, 0xD97, 0x310);
            h.idle(40);
            for (int i = 0; i < 12; i++) h.step(0, CdFlowHarness.A, false);
            int frames = h.until(() -> h.row().payload() == CdStagePayload.R31A, 3000, 0);
            System.out.printf("BOSS1 act end: %s capsule jump to R31A in %d frames, route %s%n", act3, frames, h.scenes.route());
            assertTrue(h.scenes.route().stream().anyMatch(r -> r.startsWith("stage ended")), h.scenes.route().toString());
            assertEquals(CdStagePayload.R31A, h.row().payload(), "RunBossLevel: the next zone's act 1 present");
            assertEquals(1, h.scenes.flow().zone(), "zone byte after the boss act");
            assertEquals(0, h.scenes.flow().act(), "act byte after the boss act");
        }
    }
}
