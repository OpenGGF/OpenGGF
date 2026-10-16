package com.openggf.game.session;

import com.openggf.camera.Camera;
import com.openggf.game.GameplayInputFilter;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.LevelManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestGameplayModeTeardownFailures {
    @BeforeEach
    void configureServices() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"policy", "level", "error", "shared"})
    void independentCleanupContinuesAndClearsReferencesAfterFailure(String failingStep) {
        Throwable first = failingStep.equals("error")
                ? new AssertionError("policy close failed") : new IllegalStateException("first cleanup failure");
        Throwable later = failingStep.equals("shared") ? first
                : new IllegalStateException("camera cleanup failed after reset");
        var policies = mock(WorldSessionPolicyState.class);
        doReturn(new RewindSnapshottable<Integer>() {
            public String key() { return "test-policy"; }
            public Integer capture() { return 0; }
            public void restore(Integer ignored) { }
        }).when(policies).rewindAdapter();
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:teardown") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) world -> policies)
                        : super.getGameService(type);
            }
        };
        var mode = SessionManager.openGameplaySession(module);
        Camera camera = new Camera() {
            @Override public void resetState() {
                super.resetState();
                if (later instanceof RuntimeException runtimeFailure) throw runtimeFailure;
                throw (Error) later;
            }
        };
        GameplaySessionFactory.attachManagers(mode, EngineServices.current(), camera);
        camera.setFrozen(true);
        var originalSprites = mode.getSpriteManager();
        AtomicInteger replayCloses = new AtomicInteger();
        mode.setHardwareTimingReplayCloseHook(replayCloses::incrementAndGet);
        if (failingStep.equals("level")) {
            LevelManager level = spy(mode.getLevelManager());
            doThrow(first).when(level).resetGameplayState();
            mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(),
                    mode.getTerrainCollisionManager(), mode.getCollisionSystem(), originalSprites, level);
        } else {
            doAnswer(ignored -> { throw first; }).when(policies).closeScreens();
        }

        Throwable actual = assertThrows(first.getClass(), mode::tearDownManagers);

        assertSame(first, actual, "retain the original failure rather than replacing it during cleanup");
        assertArrayEquals(failingStep.equals("shared") ? new Throwable[0] : new Throwable[]{later},
                actual.getSuppressed());
        assertEquals(1, replayCloses.get(), "early failures must not strand the replay close hook");
        assertFalse(camera.getFrozen(), "even the last manager reset must execute");
        assertFalse(mode.dynamicArtLifecycle().isRunActive(), "finish the art run despite failed cleanup");
        assertFalse(mode.isGameplayRuntimeReady());
        assertNull(mode.getLevelManager());
        assertNull(mode.getSpriteManager());
        assertNull(mode.getCamera());
        assertNull(mode.getWaterSystem());
        assertNull(mode.getRewindRegistry());
        assertSame(GameplayInputFilter.IDENTITY, mode.currentGameplayInputFilter());
        assertDoesNotThrow(mode::tearDownManagers, "cleanup is one-shot even after failure");
        assertEquals(1, replayCloses.get());
        verify(policies, times(1)).closeScreens();
    }
}
