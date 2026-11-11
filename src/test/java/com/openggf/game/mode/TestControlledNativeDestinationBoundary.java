package com.openggf.game.mode;

import com.openggf.GameLoop;
import com.openggf.LevelFrameResult;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.level.NativeLevelLoadAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Public native destination request through a real controlled fade callback, without a
 * trace timing client or synthetic boundary. Explicit course loads are a different entry.
 * The stable controller holds the source, then releases supplied native input in HCZ1.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestControlledNativeDestinationBoundary {
    private GameplayModeContext context;
    private final InputHandler input = new InputHandler();
    private final Decision controller = new Decision();
    private int filteredRows;
    private com.openggf.tools.RecordingFrameDriver recording;

    @AfterEach
    void close() {
        try {
            SessionManager.clear();
        } finally {
            GameServices.configuration().clearSessionOverrides();
        }
    }

    private void loadSource(boolean controlled) {
        var base = GameServices.module();
        TestEnvironment.configureGameModuleFixture(controlled
                ? new DelegatingGameModule(base, "test:native-destination") {
                    @Override public GameplayFrameController gameplayFrameController() { return controller; }
                } : base);
        GameServices.audio().setBackend(new NullAudioBackend());
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        context = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build().runtime();
        // HeadlessTestFixture.resetPerTest replaces EngineServices after the context
        // registered its initial timing adapter. Restore the fixture's actual current
        // engine owner, without changing any clock value or gameplay state.
        var registry = context.getRewindRegistry();
        var clock = EngineTiming.vIntRunCounter();
        registry.deregister(clock.key());
        registry.register(clock);
        assertFalse(NativeLevelLoadAccess.isPending(context.getLevelManager()));
        assertFalse(context.getLevelManager().hasPendingFreshLevelTransitionBoundary());
        assertFalse(context.getLevelManager().isTitleCardRequested());
        assertFalse(context.getFadeManager().isActive());
        GameplayInputFilterAccess.install(context, raw -> { filteredRows++; return raw; });
    }

    private LevelFrameResult controlledStep() {
        if (recording != null) return recording.stepFrame(false, false, false, true, false);
        return ControlledFrameRuntime.step(context, input,
                LogicalInputSnapshot.ofPlayers(PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT, 0,
                        0, 0, false, false), PlayerInputState.neutral()));
    }

    private void requestControlledDestination() {
        loadSource(true);
        requestDestination();
    }

    private void requestDestination() {
        for (int n = 0; n < 2; n++) controlledStep(); // consume only an actual source setup pass
        assertEquals(0, controller.advanced);
        assertEquals(0, filteredRows, "HOLD does not consume native input");
        int sourceFrames = context.getSpriteManager().getFrameCounter();
        context.getLevelManager().requestZoneAndAct(1, 0);
        int rows = 0;
        while (context.getLevelManager().getCurrentZone() != 1 && rows++ < 64) {
            assertEquals(LevelFrameResult.HELD, controlledStep());
        }
        assertEquals(1, context.getLevelManager().getCurrentZone(), "production fade callback loaded HCZ1");
        assertEquals(0, context.getLevelManager().getCurrentAct());
        assertTrue(rows > 1, "the native fade, not a direct destination load, owns the handoff");
        assertEquals(0, controller.advanced);
        assertEquals(sourceFrames, context.getSpriteManager().getFrameCounter());
        assertEquals(0, filteredRows);
        assertSame(controller, ControlledFrameRuntime.controller(context), "same controller across the load");
        assertFalse(NativeLevelLoadAccess.isPending(context.getLevelManager()),
                "LIVE S3K load assembled synchronously, with no initialization timing client");
        assertTrue(context.getLevelManager().isTitleCardRequested(), "native destination retains its normal card");
        assertTrue(context.getLevelManager().hasPendingFreshLevelTransitionBoundary());
        assertEquals(0, context.getCamera().getFocusedSprite().getCentreY(), "pre-title held player slot");
        // Loading selects the destination's input policy. Observe that selected visit
        // with an identity filter; the source observer is correctly retired by loading.
        GameplayInputFilterAccess.install(context, raw -> { filteredRows++; return raw; });
        assertFalse(new CourseControl(context).presentationReady(),
                "pending native title and fresh assembly are genuine presentation obligations");
    }

    @Test
    void nativeAdvanceWaitsForBoundedDestinationTitleAndAssemblyCompletion() {
        requestControlledDestination();
        controller.disposition = GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
        finishNativeDestination();
        assertNativeAdmission();
    }

    @Test
    void recordingHostUsesTheSameNativeDestinationAdmission() {
        loadSource(true);
        recording = new com.openggf.tools.RecordingFrameDriver(context.getCamera().getFocusedSprite());
        requestDestination();
        controller.disposition = GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
        finishNativeDestination();
        assertNativeAdmission();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, recording.getLastFrameResult());
    }

    @Test
    void holdProgressesNativePresentationThenFreezesWorldAndRepeatedLoadHasANewOwner() {
        requestControlledDestination();
        finishNativeDestination();
        for (int row = 0; row < 200 && !new CourseControl(context).presentationReady(); row++)
            assertEquals(LevelFrameResult.HELD, controlledStep());
        assertTrue(new CourseControl(context).presentationReady(), "released overlay also completes under HOLD");
        var held = new CourseControl(context).playerState();
        int frames = context.getSpriteManager().getFrameCounter();
        for (int row = 0; row < 3; row++) assertEquals(LevelFrameResult.HELD, controlledStep());
        assertEquals(held, new CourseControl(context).playerState());
        assertEquals(frames, context.getSpriteManager().getFrameCounter());
        assertEquals(0, filteredRows);
        var previousObjects = context.getLevelManager().getObjectManager();
        context.getLevelManager().requestZoneAndAct(1, 0);
        for (int row = 0; row < 64 && context.getLevelManager().getObjectManager() == previousObjects; row++)
            assertEquals(LevelFrameResult.HELD, controlledStep());
        assertNotSame(previousObjects, context.getLevelManager().getObjectManager(), "real repeated load");
        assertTrue(context.getLevelManager().isTitleCardRequested());
        assertFalse(new CourseControl(context).presentationReady());
        GameplayInputFilterAccess.install(context, raw -> { filteredRows++; return raw; });
        finishNativeDestination();
        controller.disposition = GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
        assertNativeAdmission();
    }

    @Test
    void activeDestinationPresentationRestoresAndResetHasNoHiddenHold() {
        requestControlledDestination();
        assertEquals(LevelFrameResult.HELD, controlledStep()); // title init
        for (int row = 0; row < 5; row++) assertEquals(LevelFrameResult.HELD, controlledStep());
        var snapshot = context.getRewindRegistry().capture();
        var expectedOwner = context.controlledPresentation().capture();
        assertTrue(expectedOwner.active());
        assertTrue(ControlledFrameRuntime.isNativeTitlePresentationActive(context),
                "renderers must draw the normal card even when its released-overlay flag is false");
        finishNativeDestination();
        var expected = new CourseControl(context).playerState();
        var expectedClock = EngineTiming.vIntRunCounter().capture();
        context.getRewindRegistry().restore(snapshot);
        assertEquals(expectedOwner, context.controlledPresentation().capture());
        assertFalse(new CourseControl(context).presentationReady());
        finishNativeDestination();
        assertEquals(expected, new CourseControl(context).playerState());
        assertEquals(expectedClock, EngineTiming.vIntRunCounter().capture());
        context.controlledPresentation().resetForMissingSnapshot();
        assertFalse(context.controlledPresentation().capture().active());
        controller.disposition = GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
        assertNativeAdmission();
    }

    private void finishNativeDestination() {
        var level = context.getLevelManager();
        int spriteClock = context.getSpriteManager().getFrameCounter();
        int levelClock = level.getFrameCounter();
        int objectVint = level.getObjectManager().getVblaCounter();
        boolean titleObserved = false;
        int rows = 0;
        // Stop at actual assembly. Released overlay text can survive into gameplay;
        // readiness for a reusable course checkpoint additionally waits for that tail.
        while ((level.isTitleCardRequested() || level.hasPendingFreshLevelTransitionBoundary()
                || NativeLevelLoadAccess.isPending(level)) && rows++ < 400) {
            assertFalse(new CourseControl(context).presentationReady());
            assertEquals(LevelFrameResult.HELD, controlledStep(), "presentation owns row " + rows);
            titleObserved |= !level.isTitleCardRequested();
            assertEquals(spriteClock, context.getSpriteManager().getFrameCounter());
            assertEquals(levelClock, level.getFrameCounter());
            assertEquals(0, filteredRows, "native input waits for admission");
            assertEquals(0, controller.advanced, "presentation is not gameplay");
        }
        assertTrue(rows > 1 && rows < 400, "bounded production presentation completes");
        assertTrue(titleObserved, "host consumed the normal title request");
        assertFalse(level.isTitleCardRequested());
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        assertFalse(NativeLevelLoadAccess.isPending(level));
        assertTrue(level.getObjectManager().getVblaCounter() > objectVint,
                "native title iterations own their VBlank clock even under HOLD");
        assertEquals(level.getObjectManager().getVblaCounter(), EngineTiming.vIntRunCounter().value());
        assertEquals(0x20, context.getCamera().getFocusedSprite().getCentreY(), "native HCZ1 start restored");
    }

    private void assertNativeAdmission() {
        var level = context.getLevelManager();
        int spriteClock = context.getSpriteManager().getFrameCounter();
        int levelClock = level.getFrameCounter();
        int objectVint = level.getObjectManager().getVblaCounter();
        long engineVint = EngineTiming.vIntRunCounter().value();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, controlledStep());
        assertEquals(spriteClock + 1, context.getSpriteManager().getFrameCounter());
        assertEquals(levelClock + 1, level.getFrameCounter());
        assertEquals(objectVint + 1, level.getObjectManager().getVblaCounter());
        assertEquals(engineVint + 1, EngineTiming.vIntRunCounter().value());
        assertEquals(1, filteredRows);
        assertEquals(1, controller.advanced);
        assertTrue(context.getCamera().getFocusedSprite().isRightPressed());
        assertFalse(level.isTitleCardRequested());
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
    }

    @Test
    void explicitFinishCompletesTheSameDestinationBeforeNativeInputIsAdmitted() {
        requestControlledDestination();
        controller.finishPresentation = true;
        assertEquals(LevelFrameResult.HELD, controlledStep());
        var level = context.getLevelManager();
        assertFalse(level.isTitleCardRequested());
        assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        assertEquals(0x20, context.getCamera().getFocusedSprite().getCentreY(), "native HCZ1 start restored");
        assertEquals(0, filteredRows);
        assertEquals(0, controller.advanced);
        int spriteClock = context.getSpriteManager().getFrameCounter();
        int levelClock = level.getFrameCounter();
        controller.disposition = GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, controlledStep());
        assertEquals(spriteClock + 1, context.getSpriteManager().getFrameCounter());
        assertEquals(levelClock + 1, level.getFrameCounter());
        assertEquals(1, filteredRows);
        assertEquals(1, controller.advanced);
        assertTrue(context.getCamera().getFocusedSprite().isRightPressed());
    }

    @Test
    void ordinaryHostOwnsTheNativeTitleAndCompletesAssemblyBeforeGameplay() {
        loadSource(false);
        var loop = new GameLoop(input);
        loop.setGameMode(GameMode.LEVEL);
        context.getLevelManager().requestZoneAndAct(1, 0);
        boolean titleObserved = false;
        boolean boundaryObserved = false;
        int rows = 0;
        for (; rows < 2_000; rows++) {
            loop.step();
            if (context.getLevelManager().getCurrentZone() != 1) continue;
            titleObserved |= loop.getCurrentGameMode() == GameMode.TITLE_CARD;
            boundaryObserved |= context.getLevelManager().hasPendingFreshLevelTransitionBoundary();
            if (titleObserved && !context.getLevelManager().hasPendingFreshLevelTransitionBoundary()
                    && loop.getCurrentGameMode() == GameMode.LEVEL) break;
        }
        assertTrue(titleObserved, "ordinary host consumed and entered the normal title request");
        assertTrue(boundaryObserved);
        assertTrue(rows < 2_000, "bounded title/assembly completion");
        assertFalse(context.getLevelManager().isTitleCardRequested());
        assertFalse(context.getLevelManager().hasPendingFreshLevelTransitionBoundary());
        assertFalse(NativeLevelLoadAccess.isPending(context.getLevelManager()));
        assertEquals(0x20, context.getCamera().getFocusedSprite().getCentreY());
        int spriteClock = context.getSpriteManager().getFrameCounter();
        loop.step();
        assertEquals(spriteClock + 1, context.getSpriteManager().getFrameCounter());
    }

    private static final class Decision implements GameplayFrameController {
        private GameplayFrameDisposition disposition = GameplayFrameDisposition.HOLD;
        private boolean finishPresentation;
        private int advanced;

        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
            throw new AssertionError("disposition callback owns the row");
        }
        @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
            if (finishPresentation) course.finishInitialPresentation();
            return disposition;
        }
        @Override public void afterTick(CourseControl course, boolean didAdvance) {
            if (didAdvance) advanced++;
        }
    }
}
