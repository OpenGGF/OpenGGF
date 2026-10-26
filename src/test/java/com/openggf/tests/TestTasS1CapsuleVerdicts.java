package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;
import com.openggf.game.sonic1.objects.Sonic1AnimalsObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1EggPrisonObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** S1_CAPSULE_RNG: delay a real SLZ3 button landing, change the native RNG draws,
 * and reach results sooner. Native REV01 S1NativeCapsuleProbe independently
 * measured all three controls; no observed row is supplied to the engine.
 * Entry omits the boss fight and travel, with the valid post-boss camera limit. */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1CapsuleVerdicts {
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
            f.stepIdleFrames(2);
            var body = objects.getActiveObjects().stream()
                    .filter(o -> o instanceof Sonic1EggPrisonObjectInstance)
                    .map(o -> (Sonic1EggPrisonObjectInstance) o).findFirst().orElseThrow();
            assertEquals(0x2200, body.getX());
            assertEquals(0x2bf, body.getY());
            assertEquals(2, objects.getVblaCounter(), "Declared local starting phase");
            GameServices.rng().setSeed(0x12345678L);
            f.stepIdleFrames(delay);
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
                f.stepFrame(false, false, false, false, false);
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
