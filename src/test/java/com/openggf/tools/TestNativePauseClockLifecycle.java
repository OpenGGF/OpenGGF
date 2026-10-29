package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.internal.NativePauseRewindAccess;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Actual Start edges and registered world snapshots, without a trace clock input. */
class TestNativePauseClockLifecycle {
    private static final Bk2FrameInput START = new Bk2FrameInput(0, 0, 0, true, "Start");

    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test
    void nativePauseFlagRestoresIndependentlyOfTheCurrentFlag() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var state = GameServices.gameState();
        var running = registry.capture();
        assertTrue(state.applyPauseToggle(true));
        var paused = registry.capture();
        assertFalse(state.applyPauseToggle(true));
        registry.restore(paused);
        assertTrue(state.isGamePaused(), "the native pause flag is gameplay state");
        registry.restore(running);
        assertFalse(state.isGamePaused(), "restore must also leave a later native pause");
    }

    @Test
    void registryRestoringALegacySnapshotDefaultsNativePauseToUnpaused() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var entries = new java.util.LinkedHashMap<>(registry.capture().entries());
        assertNotNull(entries.remove(NativePauseRewindAccess.KEY));
        assertTrue(GameServices.gameState().applyPauseToggle(true));
        registry.restore(new CompositeSnapshot(entries));
        assertFalse(GameServices.gameState().isGamePaused());
        assertEquals(Boolean.FALSE, registry.capture().get(NativePauseRewindAccess.KEY));
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1PauseAndRestoredInputReplayKeepOneClockTickPerRow() throws Exception {
        exercise("sonic1.rom.path");
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2PauseAndRestoredInputReplayKeepOneClockTickPerRow() throws Exception {
        exercise("sonic2.rom.path");
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kPauseAndRestoredInputReplayKeepOneClockTickPerRow() throws Exception {
        exercise("s3k.rom.path");
    }

    private static void exercise(String romProperty) throws Exception {
        // Two independent sessions in one JVM reject a retained pause latch or
        // duplicate carrier tick after unbinding/rebinding the object clock.
        for (int sessionNumber = 0; sessionNumber < 2; sessionNumber++) {
            var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
            try (var session = new GameplayCaptureSession(settings)) {
                session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
                for (int row = 0; ; row++) {
                    assertTrue(row < 600, "native level entry must finish");
                    session.step(null);
                    session.renderFrame();
                    if (session.loop().getCurrentGameMode() == GameMode.LEVEL
                            && !GameServices.level().hasPendingFreshLevelTransitionBoundary()) break;
                }
                for (int row = 0; row < 60; row++) session.step(null);
                assertFalse(GameServices.gameState().isGamePaused(), "a new session cannot inherit a disposed pause");
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                CompositeSnapshot before = registry.capture();
                long clock = EngineTiming.vIntRunCounter().value();
                session.renderFrame();

                session.step(START);
                assertPaused(before, clock + 1);
                CompositeSnapshot during = registry.capture();
                int[] pixels = session.render().pixels().clone();
                session.step(START); // held Start cannot toggle again
                assertPaused(before, clock + 2);
                assertArrayEquals(pixels, session.render().pixels(), "held pause preserves rendered gameplay");
                session.step(null); // release leaves native pause active
                assertPaused(before, clock + 3);
                assertArrayEquals(pixels, session.render().pixels(), "release retains the paused picture");
                CompositeSnapshot beforeResume = registry.capture();
                session.step(START);
                assertFalse(GameServices.gameState().isGamePaused());
                assertEquals(clock + 4, EngineTiming.vIntRunCounter().value());
                assertEquals(GameServices.level().getObjectManager().getVblaCounter(),
                        (int) EngineTiming.vIntRunCounter().value());
                CompositeSnapshot resumed = registry.capture();
                session.step(null);
                assertEquals(clock + 5, EngineTiming.vIntRunCounter().value(), "ordinary continuation ticks once");
                CompositeSnapshot after = registry.capture();

                // The external controller driver restores only its own held
                // input history; all gameplay/clock/PLC state comes from rewind.
                registry.restore(before);
                session.restoreInputHistory(null);
                assertSameState(before, registry.capture());
                session.step(START); session.step(START); session.step(null); session.step(START); session.step(null);
                assertSameState(after, registry.capture());

                registry.restore(during);
                session.restoreInputHistory(START);
                assertSameState(during, registry.capture());
                session.step(START); session.step(null); session.step(START); session.step(null);
                assertSameState(after, registry.capture());

                registry.restore(beforeResume);
                session.restoreInputHistory(null);
                session.step(START);
                assertSameState(resumed, registry.capture());
                session.step(null);
                assertSameState(after, registry.capture());

                registry.restore(resumed);
                session.restoreInputHistory(START);
                session.step(null);
                assertSameState(after, registry.capture());

                // Host focus/user holds represent no game frame. A single
                // admitted step after release must still tick exactly once.
                long hostClock = EngineTiming.vIntRunCounter().value();
                session.loop().pause(); session.step(null); session.step(null);
                assertEquals(hostClock, EngineTiming.vIntRunCounter().value());
                session.loop().resume(); session.step(null);
                assertEquals(hostClock + 1, EngineTiming.vIntRunCounter().value());
                session.loop().toggleUserPause(); session.step(null);
                assertEquals(hostClock + 1, EngineTiming.vIntRunCounter().value());
                int stepKey = SonicConfigurationService.getInstance().getInt(SonicConfiguration.FRAME_STEP_KEY);
                session.loop().getInputHandler().handleKeyEvent(stepKey, org.lwjgl.glfw.GLFW.GLFW_PRESS);
                session.step(null);
                assertEquals(hostClock + 2, EngineTiming.vIntRunCounter().value(), "host frame step admits exactly one tick");
                assertTrue(session.loop().isUserPaused());
                session.loop().getInputHandler().handleKeyEvent(stepKey, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
                session.step(null);
                assertEquals(hostClock + 2, EngineTiming.vIntRunCounter().value());
                session.loop().toggleUserPause(); session.step(null);
                assertEquals(hostClock + 3, EngineTiming.vIntRunCounter().value());
                if (sessionNumber == 0) {
                    session.step(START);
                    assertTrue(GameServices.gameState().isGamePaused(), "dispose the first world while natively paused");
                    assertEquals(hostClock + 4, EngineTiming.vIntRunCounter().value());
                }
            }
        }
    }

    private static void assertPaused(CompositeSnapshot before, long clock) {
        assertTrue(GameServices.gameState().isGamePaused());
        assertEquals(clock, EngineTiming.vIntRunCounter().value());
        var now = SessionManager.getCurrentGameplayMode().getRewindRegistry().capture();
        var objectDiff = RewindSnapshotDiff.diffKey("object-manager", before.get("object-manager"), now.get("object-manager"));
        assertEquals(1, objectDiff.size(), () -> "only the object clock may change: " + objectDiff);
        assertTrue(objectDiff.getFirst().startsWith("object-manager.vblaCounter:"), objectDiff::toString);
        // Full player, level/IGT and RNG snapshots must remain frozen.
        for (String key : List.of("sprites", "level", "gamerng", "timermanager")) {
            assertTrue(before.entries().containsKey(key), "missing frozen owner " + key);
            assertKey(before, now, key);
        }
    }

    private static void assertSameState(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) assertKey(expected, actual, key);
    }

    private static void assertKey(CompositeSnapshot expected, CompositeSnapshot actual, String key) {
        var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
        assertTrue(diff.isEmpty(), () -> key + ": " + diff);
    }
}
