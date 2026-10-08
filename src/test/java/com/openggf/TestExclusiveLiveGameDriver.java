package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameMode;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;

@Isolated
class TestExclusiveLiveGameDriver {
    private GameLoop loop;
    private InputHandler input;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        input = new InputHandler();
        loop = new GameLoop(input);
    }

    @AfterEach
    void tearDown() {
        loop.closePresence();
        SessionManager.clear();
    }

    @Test
    void lifetimeRejectsOtherProductionEntriesAndRestoresStockOwnerOnClose() {
        var siblingLoop = new GameLoop(new InputHandler());
        try (var driver = ExclusiveLiveGameDriver.acquire(loop)) {
            assertTrue(loop.externalFrameOrInputOwnerActive());
            assertThrows(IllegalStateException.class, loop::step);
            assertThrows(IllegalStateException.class, loop::stepPresentationFrame);
            assertThrows(IllegalStateException.class, siblingLoop::step);
            assertThrows(IllegalStateException.class, () -> loop.setInputHandler(new InputHandler()));
            assertThrows(IllegalStateException.class, () -> ExclusiveLiveGameDriver.acquire(siblingLoop));
        } finally { siblingLoop.closePresence(); }
        assertFalse(loop.externalFrameOrInputOwnerActive());
        try (var reopened = ExclusiveLiveGameDriver.acquire(loop)) {
            assertTrue(loop.externalFrameOrInputOwnerActive());
        }
    }

    @Test
    void nativePausePollsEveryIterationAndPhysicalPauseAndStepKeysAreExcluded() {
        var gameState = SessionManager.getCurrentGameplayMode().getGameStateManager();
        try (var driver = ExclusiveLiveGameDriver.acquire(loop)) {
            var first = driver.step(0x80);
            assertEquals(ExclusiveLiveGameDriver.Kind.NATIVE_PAUSE, first.kind());
            assertTrue(first.controllerPolled());
            assertTrue(gameState.isGamePaused());
            assertFalse(loop.isUserPaused(), "Genesis Start belongs to ROM Pause_Loop");
            input.handleKeyEvent(GLFW_KEY_F12, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_P, GLFW_PRESS);
            var held = driver.step(0x80);
            assertEquals(ExclusiveLiveGameDriver.Kind.NATIVE_PAUSE, held.kind());
            assertEquals(2, held.sequence());
            assertFalse(input.logical().player1().startPressed());
            assertTrue(gameState.isGamePaused());
            assertTrue(driver.step(0).controllerPolled(), "native pause polls the release");
            loop.pause();
            var paused = driver.step(0x80);
            assertEquals(ExclusiveLiveGameDriver.Kind.HOST_PAUSED, paused.kind());
            assertFalse(paused.controllerPolled());
            loop.resume();
        }
    }

    @Test
    void sessionCheckpointRestoresNativePollEdgesAndSequenceAndLeaseRemovesAdapter() {
        var context = SessionManager.getCurrentGameplayMode();
        var registry = context.getRewindRegistry();
        try (var driver = ExclusiveLiveGameDriver.acquire(loop)) {
            driver.step(0x80); // Pause_Loop now owns native polling.
            var checkpoint = registry.capture();
            assertTrue(checkpoint.containsKey(ExclusiveLiveGameDriver.REWIND_KEY));
            var held = driver.step(0xa0); // Held Start + newly pressed C.
            var heldInput = input.logical();
            var released = driver.step(0);
            var releasedInput = input.logical();
            registry.restore(checkpoint);
            assertTrue(context.getGameStateManager().isGamePaused());
            assertEquals(held, driver.step(0xa0));
            assertEquals(heldInput, input.logical());
            assertEquals(released, driver.step(0));
            assertEquals(releasedInput, input.logical());
        }
        assertFalse(registry.capture().containsKey(ExclusiveLiveGameDriver.REWIND_KEY));
    }

    @Test
    void automaticLiveRewindCannotCaptureInsideExclusiveIteration() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        assertThrows(IllegalStateException.class, () -> ExclusiveLiveGameDriver.acquire(loop));
        assertFalse(ExternalFrameOrInputOwnership.liveOwnerActive(loop.exclusiveLiveServices()));
    }

    @Test
    void iterationCallbacksPreserveFailureAndPausedBoundaries() {
        var calls = new java.util.ArrayList<String>();
        var productionFailure = new IllegalStateException("production failure");
        var afterFailure = new IllegalStateException("after-step failure");
        try (var driver = ExclusiveLiveGameDriver.acquire(loop)) {
            Runnable production = () -> {
                calls.add("production");
                throw productionFailure;
            };
            assertSame(productionFailure, assertThrows(IllegalStateException.class,
                    () -> driver.runIteration(production, () -> calls.add("after"),
                            () -> calls.add("presence"))));
            assertEquals(List.of("production", "after", "presence"), calls);
            calls.clear();
            assertSame(afterFailure, assertThrows(IllegalStateException.class,
                    () -> driver.runIteration(production, () -> {
                        calls.add("after");
                        throw afterFailure;
                    }, () -> calls.add("presence"))));
            assertEquals(List.of("production", "after"), calls);
            calls.clear();
            loop.pause();
            driver.runIteration(production, () -> calls.add("after"), () -> calls.add("presence"));
            assertTrue(calls.isEmpty(), "host pause admits no production or completion callbacks");
            loop.resume();
        }
    }

    @Test
    void failedAdapterAdmissionReleasesInputAndIterationOwnership() {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        registry.register(new com.openggf.game.rewind.RewindSnapshottable<Integer>() {
            @Override public String key() { return ExclusiveLiveGameDriver.REWIND_KEY; }
            @Override public Integer capture() { return 1; }
            @Override public void restore(Integer snapshot) { }
        });
        try {
            assertThrows(IllegalStateException.class, () -> ExclusiveLiveGameDriver.acquire(loop));
            assertFalse(loop.externalFrameOrInputOwnerActive());
            input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
            assertTrue(input.isKeyPressed(GLFW_KEY_F1));
        } finally { registry.deregister(ExclusiveLiveGameDriver.REWIND_KEY); }
        try (var driver = ExclusiveLiveGameDriver.acquire(loop)) {
            assertTrue(loop.externalFrameOrInputOwnerActive());
        }
    }

    @Test
    void oneCallRunsOneProductionTitleIterationWithoutPresentationPacing() throws Exception {
        SessionManager.getCurrentGameplayMode().getCamera().setFocusedSprite(
                mock(com.openggf.sprites.playable.AbstractPlayableSprite.class));
        var title = mock(TitleCardProvider.class);
        when(title.shouldRunPlayerPhysics()).thenReturn(false);
        when(title.shouldRunLevelObjectsDuringLockedPhase()).thenReturn(false);
        when(title.shouldReleaseControl()).thenReturn(false);
        when(title.shouldCompleteFreshLevelTransitionBoundary()).thenReturn(false);
        setField(loop, "titleCardProvider", title);
        loop.setGameMode(GameMode.TITLE_CARD);
        try (var driver = ExclusiveLiveGameDriver.acquire(loop)) {
            var result = driver.step(0x20);
            verify(title, times(1)).update();
            assertEquals(ExclusiveLiveGameDriver.Kind.TITLE, result.kind());
            assertTrue(result.controllerPolled());
            driver.step(0x20);
            verify(title, times(2)).update();
            assertEquals(0, input.logical().player1().actionPressedMask());
        }
    }

    @Test
    void playbackAndRecordedHardwareCannotEnterOrCompeteWithLiveDrive() {
        var playback = loop.exclusiveLiveServices().playbackDebug();
        var movie = new Bk2Movie(Path.of("ownership.bk2"), "logkey", Map.of(),
                List.of(new Bk2FrameInput(0, 0, 0, false, "neutral")), 1);
        playback.scheduleSessionAtNextLevelLoad(movie, 0);
        assertThrows(IllegalStateException.class, () -> ExclusiveLiveGameDriver.acquire(loop));
        playback.cancelScheduledLevelLoadSession();
        try (var driver = ExclusiveLiveGameDriver.acquire(loop)) {
            playback.startSession(movie, 0);
            assertThrows(IllegalStateException.class, () -> driver.step(0));
            playback.endSession();
        }
        SessionManager.getCurrentGameplayMode().activateRecordedHardwareAdmission();
        assertThrows(IllegalStateException.class, () -> ExclusiveLiveGameDriver.acquire(loop));
        assertFalse(ExternalFrameOrInputOwnership.liveOwnerActive(loop.exclusiveLiveServices()));
    }

    @Test
    void dmaOnlyVblankDoesNotCommitTheNativePollingBaseline() throws Exception {
        var provider = mock(com.openggf.game.SpecialStageProvider.class);
        setField(loop, "activeSpecialStageProvider", provider);
        loop.setGameMode(GameMode.SPECIAL_STAGE);
        SessionManager.getCurrentGameplayMode().plcFrameLifecycle().markNextVblankServicesDmaQueue();
        try (var driver = ExclusiveLiveGameDriver.acquire(loop)) {
            var dma = driver.step(0x20);
            assertEquals(ExclusiveLiveGameDriver.Kind.LAG, dma.kind());
            assertFalse(dma.controllerPolled());
            verify(provider).handleInput(0, 0, false, false);
            assertEquals(0, input.logical().player1().actionHeldMask(), "the unpolled offered byte stays hidden");
            assertTrue(driver.step(0x20).controllerPolled());
            verify(provider).handleInput(0x20, 0x20, false, false);
            assertEquals(com.openggf.control.InputActionMasks.ACTION_C,
                    input.logical().player1().actionPressedMask(), "the first actual poll derives the edge");
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = GameLoop.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
