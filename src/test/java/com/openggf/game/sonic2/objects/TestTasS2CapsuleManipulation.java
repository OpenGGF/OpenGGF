package com.openggf.game.sonic2.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 3325M/5373S, 3584M/5735S: pause skips the last V-int-gated animal and its RNG draws. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2CapsuleManipulation {
    @Test
    void pauseAdvancesVintAndOmitsTheLateAnimal() throws Exception {
        assertFalse(runLastSpawn(false, true));
        assertTrue(runLastSpawn(true, true));
        assertTrue(runLastSpawn(false, false)); // off-mask running control reaches actual results
    }

    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    private boolean runLastSpawn(boolean pauseOnSpawn, boolean onSpawnMask) throws Exception {
        TestEnvironment.resetAll();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            var level = GameServices.level();
            int waits = 0;
            do {
                com.openggf.tests.reel.TestReelCapture.step(session,null);
                session.renderFrame();
                assertTrue(++waits < 600, "fresh level entry must finish");
            } while (session.loop().getCurrentGameMode() != GameMode.LEVEL
                    || level.hasPendingFreshLevelTransitionBoundary());
            var objects = level.getObjectManager();
            var player = GameServices.sprites().getMainPlayable();
            var capsule = new EggPrisonObjectInstance(new ObjectSpawn(
                    player.getCentreX() + 96, player.getCentreY(), 0x3E, 0, 0, false, 0), "EggPrison");
            objects.addDynamicObject(capsule);
            com.openggf.tests.reel.TestReelCapture.step(session,null); // production object pass allocates the actual component graph
            assertTrue(objects.getActiveObjects().stream()
                    .anyMatch(o -> o.getClass().getSimpleName().equals("EggPrisonBrokenSlotChild")));
            // Wait on the live clock; do not seed or manually pass a V-int.
            while ((EngineTiming.vIntRunCounter().lowByte() & 7) != (onSpawnMask ? 7 : 0)) com.openggf.tests.reel.TestReelCapture.step(session,null);
            // Legal late-release state: all initial animals have already gone;
            // this child is one admitted object pass from its final checker.
            set(capsule, "brokenRoutineSecondary", 2);
            set(capsule, "brokenAnimDuration", 1);
            assertEquals(0, animalCount(objects));
            GameServices.rng().setSeed(0x12345678L); // matched declared RNG in every control
            long beforeClock = EngineTiming.vIntRunCounter().value();
            long rng = GameServices.rng().getSeed();
            int beforeFrame = GameServices.sprites().getFrameCounter();
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var beforePause = registry.capture();
            CompositeSnapshot beforeResume = null;
            com.openggf.tests.reel.TestReelCapture.step(session,pauseOnSpawn ? start() : null);
            // Historical base froze the pause clock; native Pause_Loop counts every wait.
            assertEquals(beforeClock + 1, EngineTiming.vIntRunCounter().value());
            assertEquals(pauseOnSpawn, GameServices.gameState().isGamePaused());
            assertEquals(beforeFrame + (pauseOnSpawn ? 0 : 1), GameServices.sprites().getFrameCounter());
            boolean spawned = onSpawnMask && !pauseOnSpawn;
            assertEquals(spawned ? 1 : 0, animalCount(objects));
            assertEquals(!spawned, GameServices.rng().getSeed() == rng);
            if (pauseOnSpawn) {
                com.openggf.tests.reel.TestReelCapture.step(session,start()); // held Start stays paused
                assertTrue(GameServices.gameState().isGamePaused());
                assertEquals(beforeClock + 2, EngineTiming.vIntRunCounter().value());
                assertEquals(rng, GameServices.rng().getSeed());
                com.openggf.tests.reel.TestReelCapture.step(session,null); // real release edge while native pause remains active
                assertTrue(GameServices.gameState().isGamePaused());
                assertEquals(beforeClock + 3, EngineTiming.vIntRunCounter().value(), "release is the third paused tick");
                assertEquals(rng, GameServices.rng().getSeed());
                beforeResume = registry.capture();
                assertEquals(0, animalCount(objects));
                com.openggf.tests.reel.TestReelCapture.step(session,start()); // fresh Start edge, production resume
                assertFalse(GameServices.gameState().isGamePaused());
                assertEquals(beforeClock + 4, EngineTiming.vIntRunCounter().value(), "resume advances exactly once");
                assertEquals(3, EngineTiming.vIntRunCounter().lowByte() & 7,
                        "pause moves the last release pass off the modulo-eight spawn mask");
                assertEquals(0, animalCount(objects));
                assertEquals(rng, GameServices.rng().getSeed());
            }
            com.openggf.tests.reel.TestReelCapture.step(session,null); // the real final checker executes on the next admitted pass
            boolean triggered = (boolean) field(capsule, "resultsTriggered").get(capsule);
            assertEquals(pauseOnSpawn || !onSpawnMask, triggered, "only the on-mask running animal blocks results");
            assertEquals(pauseOnSpawn || !onSpawnMask, objects.getActiveObjects().stream()
                    .anyMatch(ResultsScreenObjectInstance.class::isInstance));
            if (pauseOnSpawn) {
                var after = registry.capture();
                // Both restored boundaries carry neutral external input, just
                // like the completed checker row. No driver or gameplay state
                // is fabricated to restore the controller history.
                registry.restore(beforeResume);
                com.openggf.tests.reel.TestReelCapture.step(session,start());
                com.openggf.tests.reel.TestReelCapture.step(session,null);
                assertSameState(after, registry.capture());
                registry.restore(beforePause);
                com.openggf.tests.reel.TestReelCapture.step(session,start());
                com.openggf.tests.reel.TestReelCapture.step(session,start());
                com.openggf.tests.reel.TestReelCapture.step(session,null);
                com.openggf.tests.reel.TestReelCapture.step(session,start());
                com.openggf.tests.reel.TestReelCapture.step(session,null);
                assertSameState(after, registry.capture());
            }
            return triggered;
        }
    }

    private static void assertSameState(CompositeSnapshot expected, CompositeSnapshot actual) {
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> key + ": " + diff);
        }
    }

    private static Bk2FrameInput start() { return new Bk2FrameInput(0, 0, 0, true, "Start"); }

    private static long animalCount(ObjectManager objects) {
        return objects.getActiveObjects().stream().filter(o -> o.getSpawn().objectId() == 0x28).count();
    }
    private static Field field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void set(Object owner, String name, Object value) throws Exception { field(owner, name).set(owner, value); }
}
