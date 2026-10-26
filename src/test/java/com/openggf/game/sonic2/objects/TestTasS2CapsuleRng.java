package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.GameMode;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.objects.badniks.NebulaBadnikInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

/** A real enemy defeat consumes RNG before the capsule's last animal; no RNG rows hydrate gameplay. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2CapsuleRng {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }
    @Test void precedingEnemyDefeatChangesTheLastAnimalAndItsResultsDelay() throws Exception {
        var ordinary = run(false);
        var manipulated = run(true);
        assertNotEquals(ordinary.seed(), manipulated.seed(), "actual defeat must consume RNG");
        assertNotEquals(ordinary.x(), manipulated.x(), "the capsule consumes the changed stream for its spawn");
        assertTrue(ordinary.resultsDelay() < manipulated.resultsDelay(),
                "omitting this optional defeat saves a frame at the actual results transition");
    }
    private record Outcome(long seed, int x, int resultsDelay) { }
    private Outcome run(boolean defeat) throws Exception {
        TestEnvironment.resetAll();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            int waits = 0;
            do { session.step(null); session.renderFrame(); assertTrue(++waits < 600); }
            while (session.loop().getCurrentGameMode() != GameMode.LEVEL
                    || GameServices.level().hasPendingFreshLevelTransitionBoundary());
            var objects = GameServices.level().getObjectManager();
            var player = GameServices.sprites().getMainPlayable();
            while (player.getAir() && waits++ < 800) session.step(null);
            assertFalse(player.getAir());
            // Equal, valid pre-contact state and RNG in both branches. The optional
            // enemy is then defeated by the actual player touch and object passes.
            GameServices.rng().setSeed(0x12345678L);
            player.setRolling(true); player.setAnimationId(Sonic2AnimationIds.ROLL);
            player.setGSpeed((short) 0x300); player.setXSpeed((short) 0x300);
            var enemy = new NebulaBadnikInstance(new ObjectSpawn(player.getCentreX() + 3, player.getCentreY(),
                    0x99, 0x12, 0, false, 0));
            if (defeat) objects.addDynamicObject(enemy);
            session.step(null);
            if (defeat) { assertTrue(enemy.isDestroyed()); assertEquals(100, GameServices.gameState().getScore()); }
            for (int i = 0; i < 240; i++) session.step(null);
            assertTrue(objects.getActiveObjects().stream().noneMatch(o -> o.getSpawn().objectId() == 0x28),
                    "the earlier enemy animal must actually leave before capsule timing starts");
            long seed = GameServices.rng().getSeed();
            assertEquals(!defeat, seed == 0x12345678L);
            var capsule = new EggPrisonObjectInstance(new ObjectSpawn(player.getCentreX() + 96,
                    player.getCentreY(), 0x3E, 0, 0, false, 0), "EggPrison");
            objects.addDynamicObject(capsule); session.step(null);
            while ((EngineTiming.vIntRunCounter().lowByte() & 7) != 7) session.step(null);
            // Declared late-release child state: all previous animals have left.
            set(capsule, "brokenRoutineSecondary", 2); set(capsule, "brokenAnimDuration", 1);
            session.step(null);
            var animal = objects.getActiveObjects().stream().filter(EggPrisonAnimalInstance.class::isInstance)
                    .map(EggPrisonAnimalInstance.class::cast).findFirst().orElseThrow();
            int x = animal.getX() - capsule.getX();
            assertNotEquals(seed, GameServices.rng().getSeed());
            int delay = 0;
            while (!(boolean) field(capsule, "resultsTriggered").get(capsule) && delay++ < 600) session.step(null);
            assertTrue(delay < 600, "actual animal departure must release results");
            assertTrue(objects.getActiveObjects().stream().anyMatch(ResultsScreenObjectInstance.class::isInstance));
            return new Outcome(seed, x, delay);
        }
    }
    private static Field field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void set(Object owner, String name, Object value) throws Exception { field(owner, name).set(owner, value); }
}
