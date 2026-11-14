package com.openggf.game.soniccd.flow;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.program.CdStageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Collision Chaos act 3's end on the campaign route, as {@link TestPpzBossActEndFlow}: a New Game, the acts before
 * reported through the flow as the results object reports them (Palmtree Panic 1-3, Collision Chaos 1-2; no good
 * future, so act 3 is the bad future R33D), then on R33D the player breaks the flower capsule from where the native
 * R33D-fight movie broke it: on the capsule's floor (player Y $10C, camera Y $60 at U+2120..2140) running right and
 * jumping into it (capsule $15 at $538,$E0 in the act 3 layout, r3/stage_object_maps_3.asm:46). The capsule's
 * {@code StartResults} spawns the results object; its {@code ObjResults_NextLevel} reports the act's end and IPX's
 * {@code RunBossLevel} goes on to the next zone's first act (IPX.asm:606-644): Tidal Tempest act 1 present, R41A.
 */
@EnabledOnOs(OS.LINUX)
class TestCczBossActEndFlow {
    @Test
    void breakingTheR33dCapsuleEndsTheZoneAndLoadsTidalTempestActOne() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            java.util.List<Set<CdStagePayload>> route = java.util.List.of(Set.of(CdStagePayload.R12A),
                    Set.of(CdStagePayload.R13C, CdStagePayload.R13D), Set.of(CdStagePayload.R31A),
                    Set.of(CdStagePayload.R32A), Set.of(CdStagePayload.R33C, CdStagePayload.R33D));
            for (Set<CdStagePayload> next : route) {
                var state = GameServices.gameState();
                h.scenes.stageEnded(CdStageResult.cleared(state.getLives(), CdStageResult.TIME_PRESENT,
                        h.timeTravel().goodFuture(), h.timeTravel().timeStones()), GameServices.level());
                h.until(() -> next.contains(h.row().payload()), 900, 0);
            }
            assertEquals(CdStagePayload.R33D, h.row().payload(), "no good future: the bad future's act 3");
            h.until(() -> !h.row().ctrlLocked(), 900, 0);
            h.place(0x4E0, 0x10C, 0x440, 0x60);
            h.idle(20);
            for (int i = 0; i < 40; i++) h.step(CdFlowHarness.RIGHT, i >= 16 && i < 32 ? CdFlowHarness.A : 0, false);
            int frames = h.until(() -> h.row().payload() == CdStagePayload.R41A, 3000, 0);
            System.out.printf("CCZBOSS act end: R33D capsule to R41A in %d frames, route %s%n", frames, h.scenes.route());
            assertTrue(h.scenes.route().stream().anyMatch(r -> r.startsWith("stage ended")), h.scenes.route().toString());
            assertEquals(CdStagePayload.R41A, h.row().payload(), "RunBossLevel: the next zone's act 1 present");
            assertEquals(2, h.scenes.flow().zone(), "zone byte after the boss act");
            assertEquals(0, h.scenes.flow().act(), "act byte after the boss act");
        }
    }
}
