package com.openggf.tests;

import com.openggf.tests.reel.S1ReelFrames;

import com.openggf.game.GameServices;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;
import com.openggf.game.sonic1.objects.Sonic1AnimalsObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1EggPrisonObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.openggf.LevelFrameResult;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.rewind.RewindSnapshotDiff;

import static org.junit.jupiter.api.Assertions.*;

/** S1_CAPSULE_RNG: delay a real SLZ3 button landing, change the native RNG draws,
 * and reach results sooner. Native REV01 S1NativeCapsuleProbe independently
 * measured all three controls; no observed row is supplied to the engine.
 * Entry omits the boss fight and travel, with the valid post-boss camera limit. */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1CapsuleVerdicts {
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8, 9})
    void pauseResiduesShiftRealCapsuleAnimalAndRngPhaseAndReplay(int pausedTicks) throws Exception {
        var level = SharedLevel.load(SonicGame.SONIC_1, 4, 2);
        try {
            var f = HeadlessTestFixture.builder().withSharedLevel(level)
                    .startPosition((short) 0x2190, (short) 0x280).startPositionIsCentre().build();
            ((Sonic1LevelEventManager) GameServices.module().getLevelEventProvider()).setEventRoutine(4);
            GameServices.gameState().setCurrentBossId(0);
            f.camera().setMaxX((short) 0x2160);
            S1ReelFrames.idle(f, 2);
            var objects = GameServices.level().getObjectManager();
            var body = objects.activeObjectsOfType(Sonic1EggPrisonObjectInstance.class).getFirst();
            var player = f.sprite();
            GameServices.rng().setSeed(0x12345678L);
            player.setCentreX((short) 0x2200); player.setCentreY((short) 0x270);
            player.setAir(true); player.setYSpeed((short) 0x100);
            player.setXSpeed((short) 0); player.setGSpeed((short) 0);
            var state = body.getClass().getDeclaredField("state"); state.setAccessible(true);
            var triggered = body.getClass().getDeclaredField("buttonTriggered"); triggered.setAccessible(true);
            for (int row = 0; ; row++) {
                assertTrue(row < 100, "real button descent must reach Pri_Animals");
                S1ReelFrames.idle(f, 1);
                if (state.get(body).toString().equals("ANIMAL_SPAWN") && (objects.getVblaCounter() & 7) == 7) break;
            }
            assertTrue(triggered.getBoolean(body), "the capsule was opened by the real button contact");
            assertTrue(objects.activeObjectsOfType(Sonic1AnimalsObjectInstance.class).size() >= 8);
            var registry = f.gameplayMode().getRewindRegistry();
            var before = registry.capture();
            long rng = GameServices.rng().getSeed();
            int clock = objects.getVblaCounter();
            int animals = objects.activeObjectsOfType(Sonic1AnimalsObjectInstance.class).size();
            com.openggf.game.rewind.CompositeSnapshot expected = null;
            for (int replay = 0; replay < 2; replay++) {
                if (replay != 0) {
                    registry.restore(before);
                    f.runner().primeInputState(new Bk2FrameInput(0, 0, 0, false, "restored neutral"));
                }
                for (int tick = 0; tick < pausedTicks; tick++) {
                    assertEquals(LevelFrameResult.PAUSED, f.runner().stepFrame(false, false, false, false, false, tick == 0));
                    S1ReelFrames.afterPauseStep(f, tick == 0, true);
                    assertEquals(clock + tick + 1, objects.getVblaCounter());
                    assertEquals(rng, GameServices.rng().getSeed(), "paused animals cannot consume RNG");
                    assertEquals(animals, objects.activeObjectsOfType(Sonic1AnimalsObjectInstance.class).size());
                }
                assertEquals(LevelFrameResult.GAMEPLAY_FRAME, f.runner().stepFrame(false, false, false, false, false, true));
                S1ReelFrames.afterPauseStep(f, true, false);
                assertEquals(clock + pausedTicks + 1, objects.getVblaCounter());
                boolean spawn = (pausedTicks & 7) == 0;
                assertEquals(animals + (spawn ? 1 : 0), objects.activeObjectsOfType(Sonic1AnimalsObjectInstance.class).size());
                assertEquals(!spawn, rng == GameServices.rng().getSeed(), "Pri_Animals consumes RNG only on its live spawn phase");
                if (replay == 0) expected = registry.capture();
                else {
                    var actual = registry.capture();
                    for (String key : expected.entries().keySet()) {
                        var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
                        assertTrue(diff.isEmpty(), () -> key + ": " + diff);
                    }
                }
            }
        } finally { level.dispose(); }
    }

    @ParameterizedTest
    @CsvSource({"0,9,22,366", "3,8,24,345", "8,9,22,365"})
    void approachDelayChangesAnimalsAndCanSaveMoreTimeThanItCosts(
            int delay, int earlyAnimals, int lateAnimals, int resultsFrame) throws Exception {
        var level = SharedLevel.load(SonicGame.SONIC_1, 4, 2);
        try {
            var f = HeadlessTestFixture.builder().withSharedLevel(level)
                    .startPosition((short) 0x2190, (short) 0x280).startPositionIsCentre().build();
            ((Sonic1LevelEventManager) GameServices.module().getLevelEventProvider()).setEventRoutine(4);
            GameServices.gameState().setCurrentBossId(0);
            f.camera().setMaxX((short) 0x2160); // BSLZ_Escape final right boundary.
            var s = f.sprite();
            var objects = GameServices.level().getObjectManager();
            S1ReelFrames.idle(f,2);
            var body = objects.getActiveObjects().stream()
                    .filter(o -> o instanceof Sonic1EggPrisonObjectInstance)
                    .map(o -> (Sonic1EggPrisonObjectInstance) o).findFirst().orElseThrow();
            assertEquals(0x2200, body.getX());
            assertEquals(0x2bf, body.getY());
            assertEquals(2, objects.getVblaCounter(), "Declared local starting phase");
            GameServices.rng().setSeed(0x12345678L);
            S1ReelFrames.idle(f,delay);
            // Valid pre-contact descent. The button contact itself is not seeded.
            s.setCentreX((short) 0x2200);
            s.setCentreY((short) 0x270);
            s.setAir(true);
            s.setYSpeed((short) 0x100);
            s.setXSpeed((short) 0);
            s.setGSpeed((short) 0);
            var triggered = body.getClass().getDeclaredField("buttonTriggered");
            triggered.setAccessible(true);
            var results = body.getClass().getDeclaredField("resultsTriggered");
            results.setAccessible(true);
            assertFalse(triggered.getBoolean(body));
            int triggerFrame = -1;
            int actualResultsFrame = -1;
            for (int n = 0; n < 400; n++) {
                S1ReelFrames.step(f,false, false, false, false, false);
                if (triggerFrame < 0 && triggered.getBoolean(body)) triggerFrame = n;
                if (n == 70 || n == 210) {
                    long animals = objects.getActiveObjects().stream()
                            .filter(o -> o instanceof Sonic1AnimalsObjectInstance).count();
                    assertEquals(n == 70 ? earlyAnimals : lateAnimals, animals);
                    long expectedRng = n == 70
                            ? (delay == 3 ? 0x66eb1278L : 0x70ddf538L)
                            : (delay == 3 ? 0x7545e978L : 0x0368ecb8L);
                    assertEquals(expectedRng, GameServices.rng().getSeed());
                    assertFalse(results.getBoolean(body));
                }
                if (results.getBoolean(body)) {
                    actualResultsFrame = n;
                    break;
                }
            }
            assertEquals(6, triggerFrame, "Every variant really lands on the same native button");
            assertEquals(resultsFrame, actualResultsFrame,
                    "Animal departure and the released-ROM end scan must actually reach results");
            if (delay == 3) assertEquals(18, 366 - (delay + actualResultsFrame),
                    "The changed pattern saves 21 post-contact ticks for a net gain of 18");
            if (delay == 8) assertTrue(delay + actualResultsFrame > 366,
                    "Delaying is not automatically beneficial");
        } finally {
            level.dispose();
        }
    }
}
