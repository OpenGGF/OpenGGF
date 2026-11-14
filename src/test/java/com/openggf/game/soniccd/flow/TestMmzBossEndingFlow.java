package com.openggf.game.soniccd.flow;

import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.program.CdStageResult;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Metallic Madness act 3's end in a running engine, on the campaign route: a New Game whose twenty act clears are
 * reported through the flow (all good futures, or none), then R83C or R83D. The player is placed in the Hotaru
 * room (X $C40); the intro runs; each firefly is destroyed while it is open (the touch's attack path,
 * {@code onPlayerAttack}); the player is placed past X $F00, which starts Eggman; every time Eggman's collision is
 * on, the touch's attack path clears it (a hit). After the fourth hit the boss explodes and escapes, Amy runs and the
 * screen whitens; her {@code restart_stage} returns the act to IPX, whose {@code GameDone} (IPX.asm:301-326) starts
 * the ending: good with seven good futures, bad otherwise. The hits are synthetic (no recorded fight reaches the
 * arena yet); everything after them is the engine's own.
 */
@EnabledOnOs(OS.LINUX)
class TestMmzBossEndingFlow {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void defeatingTheFinalBossStartsTheEnding(boolean goodFutures) throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            for (int stage = 0; stage < 20; stage++) {
                CdStagePayload before = h.row().payload();
                var state = GameServices.gameState();
                h.scenes.stageEnded(CdStageResult.cleared(state.getLives(), CdStageResult.TIME_PRESENT,
                        goodFutures, h.timeTravel().timeStones()), GameServices.level());
                h.until(() -> h.row().payload() != null && h.row().payload() != before
                        && h.loop.getCurrentGameMode() == GameMode.LEVEL, 1200, 0);
            }
            CdStagePayload act3 = h.row().payload();
            assertEquals(goodFutures ? CdStagePayload.R83C : CdStagePayload.R83D, act3, "the final act 3 file");
            h.until(() -> !h.row().ctrlLocked(), 900, 0);

            // The Hotaru room: the intro starts at player X $BD0.
            h.place(0xC40, 0x1C0, 0xBA0, 0x100);
            int fireflies = 0;
            for (int f = 0; f < 4000 && find("MmzBossEggman").isEmpty(); f++) {
                for (AbstractObjectInstance firefly : find("MmzBossFirefly")) {
                    if (((TouchResponseProvider) firefly).getCollisionFlags() != 0 && !firefly.isDestroyed()) {
                        ((TouchResponseAttackable) firefly).onPlayerAttack(h.player(), null);
                        fireflies++;
                    }
                }
                h.step(0, 0, false);
            }
            assertEquals(3, fireflies, "three fireflies (one, then two together)");
            assertTrue(!find("MmzBossEggman").isEmpty(), "Eggman after the intro");

            // Past X $F00: Eggman's routine 2 starts the fight; his collision is attacked whenever it is on (a hit, or
            // during the panel drop, routine $1E, just the re-enable), until the act ends in the ending.
            h.place(0xF20, 0x1C0, 0xE60, 0x10C);
            int attacks = 0;
            int frames = 0;
            for (; frames < 30000 && h.loop.getCurrentGameMode() != GameMode.ENDING_CUTSCENE; frames++) {
                var eggman = find("MmzBossEggman");
                if (!eggman.isEmpty() && ((TouchResponseProvider) eggman.getFirst()).getCollisionFlags() != 0) {
                    ((TouchResponseAttackable) eggman.getFirst()).onPlayerAttack(h.player(), null);
                    attacks++;
                }
                h.step(0, 0, false);
            }
            assertEquals(GameMode.ENDING_CUTSCENE, h.loop.getCurrentGameMode(), "the ending after " + attacks + " attacks");
            assertTrue(attacks >= 4, "four hits at least");
            System.out.printf("MMZ boss: %s fight to the ending in %d frames, %d attacks, route %s%n", act3, frames, attacks, h.scenes.route());
            assertTrue(h.scenes.route().stream().anyMatch(r -> r.startsWith("stage ended")), h.scenes.route().toString());
            var ending = h.scenes.pendingEnding();
            assertNotNull(ending, "GameDone's ending is running");
            assertEquals(goodFutures, ending.good(), "good ending with seven good futures");
        }
    }

    /**
     * Rewind of the boss graph mid-fight on the same route (R83C): after the second hit the session's rewind registry
     * captures, 120 frames run, the capture is restored and the same 120 frames run again. Both passes must give the
     * same graph frame by frame (every part's class, slot, 16.16 position, routine, frame and collision), and the
     * restore must rebuild the graph captured.
     */
    @org.junit.jupiter.api.Test
    void bossGraphRestoresAndReplaysForward() throws Exception {
        try (CdFlowHarness h = new CdFlowHarness(null)) {
            h.newGame();
            for (int stage = 0; stage < 20; stage++) {
                CdStagePayload before = h.row().payload();
                var state = GameServices.gameState();
                h.scenes.stageEnded(CdStageResult.cleared(state.getLives(), CdStageResult.TIME_PRESENT, true,
                        h.timeTravel().timeStones()), GameServices.level());
                h.until(() -> h.row().payload() != null && h.row().payload() != before
                        && h.loop.getCurrentGameMode() == GameMode.LEVEL, 1200, 0);
            }
            h.until(() -> !h.row().ctrlLocked(), 900, 0);
            h.place(0xC40, 0x1C0, 0xBA0, 0x100);
            for (int f = 0; f < 4000 && find("MmzBossEggman").isEmpty(); f++) {
                for (AbstractObjectInstance firefly : find("MmzBossFirefly")) {
                    if (((TouchResponseProvider) firefly).getCollisionFlags() != 0) {
                        ((TouchResponseAttackable) firefly).onPlayerAttack(h.player(), null);
                    }
                }
                h.step(0, 0, false);
            }
            h.place(0xF20, 0x1C0, 0xE60, 0x10C);
            int attacks = 0;
            for (int f = 0; f < 20000 && attacks < 2; f++) {
                var eggman = find("MmzBossEggman");
                if (!eggman.isEmpty() && ((TouchResponseProvider) eggman.getFirst()).getCollisionFlags() != 0) {
                    ((TouchResponseAttackable) eggman.getFirst()).onPlayerAttack(h.player(), null);
                    attacks++;
                }
                h.step(0, 0, false);
            }
            h.idle(200);
            var registry = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
            String atCapture = graph();
            var snapshot = registry.capture();
            List<String> first = new ArrayList<>(), second = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                h.step(0, 0, false);
                first.add(graph());
            }
            registry.restore(snapshot);
            String restored = graph();
            for (int i = 0; i < 120; i++) {
                h.step(0, 0, false);
                second.add(graph());
            }
            System.out.println("MMZ boss rewind graph " + atCapture);
            assertTrue(atCapture.contains("MmzBossPanel") && atCapture.contains("MmzBossEggMobile"), atCapture);
            assertEquals(atCapture, restored, "restore rebuilds the captured boss graph");
            assertEquals(first, second, "the restored graph replays forward identically");
        }
    }

    private static String graph() {
        java.util.TreeMap<Integer, String> slots = new java.util.TreeMap<>();
        for (var o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof com.openggf.game.soniccd.objects.boss.CdBossPart part) || part.isDestroyed()) continue;
            slots.put(part.getSlotIndex(), String.format("%s x=%08X y=%08X rt=%02X fr=%02X col=%02X",
                    part.getClass().getSimpleName(), part.xLong(), part.yLong(), part.routine(), part.mapFrame(),
                    part.colType()));
        }
        return slots.toString();
    }

    private static List<AbstractObjectInstance> find(String simpleName) {
        List<AbstractObjectInstance> out = new ArrayList<>();
        var objects = GameServices.level().getObjectManager();
        if (objects == null) return out;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof AbstractObjectInstance o && !o.isDestroyed()
                    && o.getClass().getSimpleName().equals(simpleName)) {
                out.add(o);
            }
        }
        return out;
    }
}
