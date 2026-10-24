package com.openggf.game.mode;

import com.openggf.LevelFrameResult;
import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.*;
import com.openggf.game.internal.ControlledVBlankOnlyOwner;
import com.openggf.game.internal.NativePlayableSlot;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/** ROM-free production controlled-runtime/lifecycle tests; all Java is intentionally unrun here. */
class TestControlledVBlankOnlyOwner {
    private final List<String> events = new ArrayList<>();
    private final RecordingProfile profile = new RecordingProfile();
    private final RecordingService service = new RecordingService();
    private final RecordingFade fade = new RecordingFade();
    private GameplayModeContext context;
    private GameplayFrameController decision;

    @AfterEach void close() { SessionManager.clear(); }

    private void load(GameplayFrameController controller) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        decision = controller;
        var base = GameModuleRegistry.getCurrent();
        TestEnvironment.configureGameModuleFixture(new DelegatingGameModule(base, "test:held-vblank") {
            @Override public GameplayFrameController gameplayFrameController() { return decision; }
            @Override public LevelInitProfile getLevelInitProfile() { return profile; }
            @Override public TitleCardProvider getTitleCardProvider() { return null; }
            @Override public <T> T getGameService(Class<T> type) {
                return type == PlcLifecycleService.class ? type.cast(service) : super.getGameService(type);
            }
            @Override public RuntimeArtCoordinator createRuntimeArtCoordinator(
                    com.openggf.game.timing.HardwareTimingService timing) {
                return new RuntimeArtCoordinator() {
                    @Override public void beforeTimingService(HardwareServiceBoundary boundary) {
                        events.add("hardware-before:" + boundary);
                    }
                    @Override public void afterTimingService(HardwareServiceBoundary boundary) {
                        events.add("hardware-after:" + boundary);
                    }
                };
            }
        });
        context = TestEnvironment.activeGameplayMode();
        context.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(), fade,
                new GameRng(GameRng.Flavour.S1_S2), new DefaultSolidExecutionRegistry());
        context.setHardwareTimingBoundaryObserver(boundary -> events.add("boundary:" + boundary));
        context.getLevelManager().setFrameCounter(41);
        context.getSpriteManager().setFrameCounter(17);
        context.getSpriteManager().addSprite(new NativeProbe());
        EngineTiming.vIntRunCounter().seed(99);
        events.clear();
    }

    private LevelFrameResult step() {
        // Audio is owned by the caller, matching ControlledLevelIteration. No device or rendering.
        return ControlledFrameRuntime.step(context, new InputHandler(), LogicalInputSnapshot.neutral(),
                true, () -> fail("held row must not enter beforeGameplay"));
    }

    @Test void runtimeOwnedAudioStartsOnlyAfterTheOptInDecisionAndPhaseAreValidated() {
        var observed = new ArrayList<Long>();
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.NORMAL_PAUSE) {
            @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
                observed.add(com.openggf.game.GameServices.audio().commandTimeline().currentFrame());
                return super.beforeFrame(course, input);
            }
        };
        load(controller);
        long before = com.openggf.game.GameServices.audio().commandTimeline().currentFrame();
        assertEquals(LevelFrameResult.HELD, ControlledFrameRuntime.step(context,
                new InputHandler(), LogicalInputSnapshot.neutral(), false));
        assertEquals(List.of(before), observed);
        assertEquals(before + 1, com.openggf.game.GameServices.audio().commandTimeline().currentFrame());
        assertEquals(41, context.getLevelManager().getFrameCounter());
        assertEquals(1, controller.after);
    }

    @Test void invalidOptInPhaseDoesNotAdvanceRuntimeOwnedAudio() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.ORDINARY_LEVEL);
        load(controller);
        long before = com.openggf.game.GameServices.audio().commandTimeline().currentFrame();
        assertThrows(IllegalArgumentException.class, () -> ControlledFrameRuntime.step(context,
                new InputHandler(), LogicalInputSnapshot.neutral(), false));
        assertEquals(before, com.openggf.game.GameServices.audio().commandTimeline().currentFrame());
        assertEquals(List.of("before", "phase"), events);
        assertEquals(0, controller.after);
        assertEquals(99, EngineTiming.vIntRunCounter().value());
    }

    @Test void existingControllerAndNullOptInKeepTheUnservicedHoldAndEntryMusicContract() {
        for (Decision controller : List.of(new Decision(), new HeldDecision(null))) {
            load(controller);
            profile.musicPending = true;
            assertEquals(LevelFrameResult.HELD, step());
            assertEquals(controller instanceof HeldDecision
                    ? List.of("before", "phase", "game-vblank", "after:false")
                    : List.of("before", "game-vblank", "after:false"), events);
            assertEquals(99, EngineTiming.vIntRunCounter().value());
            assertEquals(41, context.getLevelManager().getFrameCounter());
            assertEquals(17, context.getSpriteManager().getFrameCounter());
            assertEquals(1, controller.after);
            profile.musicPending = false;
        }
    }

    @Test void servicedPauseAndLagUseOneTokenOneGameVblankAndOnlyTheVintBoundary() {
        for (PlcLifecyclePhase phase : List.of(PlcLifecyclePhase.NORMAL_PAUSE, PlcLifecyclePhase.LAG,
                PlcLifecyclePhase.SPECIAL_STAGE_PAUSE)) {
            HeldDecision controller = new HeldDecision(phase);
            load(controller);
            profile.musicPending = true; // Must not also service the ordinary held-entry fallback.
            var timers = context.getTimerManager().capture();
            var camera = context.getCamera().capture();
            var rng = context.getRng().capture();
            assertEquals(LevelFrameResult.HELD, step());
            assertEquals(List.of("before", "phase", "fade", "service:" + phase,
                    "game-vblank", "hardware-before:VINT_SERVICE", "hardware-after:VINT_SERVICE",
                    "boundary:VINT_SERVICE", "after:false"), events);
            assertEquals(100, EngineTiming.vIntRunCounter().value());
            assertEquals(41, context.getLevelManager().getFrameCounter());
            assertEquals(17, context.getSpriteManager().getFrameCounter());
            assertEquals(timers, context.getTimerManager().capture());
            assertEquals(camera, context.getCamera().capture());
            assertEquals(rng, context.getRng().capture());
            assertEquals(1, controller.after);
            assertEquals(1, controller.phaseReads);
            assertEquals(100, service.clockAtService, "the represented engine clock is already serviced");
            assertTokenCompleted();
            profile.musicPending = false;
        }
    }

    @Test void nullPhaseWithoutPendingMusicHasNoLifecycleOrPresentationService() {
        load(new HeldDecision(null));
        assertEquals(LevelFrameResult.HELD, step());
        assertEquals(List.of("before", "phase", "after:false"), events);
        assertEquals(99, EngineTiming.vIntRunCounter().value());
    }

    @Test void ownedTokenCompletionRunsOnceBeforeTheHeldAfterTick() {
        load(new HeldDecision(PlcLifecyclePhase.NORMAL_PAUSE));
        service.onService = () -> context.plcFrameLifecycle().afterConsumedIteration(
                () -> events.add("completed"));
        assertEquals(LevelFrameResult.HELD, step());
        assertEquals(List.of("before", "phase", "fade", "service:NORMAL_PAUSE", "game-vblank",
                "hardware-before:VINT_SERVICE", "hardware-after:VINT_SERVICE", "boundary:VINT_SERVICE",
                "completed", "after:false"), events);
    }

    @Test void aBoundObjectVblankClockAdvancesOnceWithoutExecutingItsObjectPass() {
        load(new HeldDecision(PlcLifecyclePhase.NORMAL_PAUSE));
        var objects = new ObjectManager(List.of(), null, -1, null, null);
        objects.initVblaCounter(99);
        var original = context.getLevelManager();
        var level = spy(original);
        doReturn(objects).when(level).getObjectManager();
        context.attachLevelManagers(context.getWaterSystem(), context.getParallaxManager(),
                context.getTerrainCollisionManager(), context.getCollisionSystem(),
                context.getSpriteManager(), level);
        int objectFrames = objects.getFrameCounter();
        assertEquals(LevelFrameResult.HELD, step());
        assertEquals(100, EngineTiming.vIntRunCounter().value());
        assertEquals(100, objects.getVblaCounter());
        assertEquals(objectFrames, objects.getFrameCounter());
        assertEquals(41, level.getFrameCounter());
    }

    @Test void fadeOwnerKeepsTheTokenAcrossItsCompletionAndGameVblankRunsOnceBeforeCallback() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.LAG);
        load(controller);
        var blocking = context.plcFrameLifecycle().beginNativeBlockingFade();
        fade.completion = blocking.wrapCompletion(() -> events.add("fade-complete"));
        assertEquals(LevelFrameResult.HELD, step());
        assertEquals(List.of("before", "phase", "service:PALETTE_FADE", "game-vblank", "fade",
                "fade-complete", "prepare:PALETTE_FADE", "hardware-before:VINT_SERVICE",
                "hardware-after:VINT_SERVICE", "boundary:VINT_SERVICE", "after:false"), events);
        events.clear();
        assertEquals(LevelFrameResult.HELD, step());
        assertEquals(List.of("before", "phase", "fade", "service:LAG", "game-vblank",
                "hardware-before:VINT_SERVICE", "hardware-after:VINT_SERVICE",
                "boundary:VINT_SERVICE", "after:false"), events);
        assertEquals(101, EngineTiming.vIntRunCounter().value());
        assertEquals(2, controller.after);
    }

    @Test void unsupportedPhaseIsRejectedBeforePresentationClockFadeOrNativeService() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.ORDINARY_LEVEL);
        load(controller);
        assertThrows(IllegalArgumentException.class, this::step);
        assertEquals(List.of("before", "phase"), events);
        assertEquals(99, EngineTiming.vIntRunCounter().value());
        assertEquals(41, context.getLevelManager().getFrameCounter());
        assertEquals(0, controller.after);
        assertTokenCompleted();
    }

    @Test void destinationTransitionKeepsItsExistingFadePathWithoutAddingAHeldToken() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.NORMAL_PAUSE);
        load(controller);
        var blocking = context.plcFrameLifecycle().beginNativeBlockingFade();
        context.getLevelManager().setLevelInactiveForTransition(true);
        try {
            assertEquals(LevelFrameResult.HELD, step());
            assertEquals(List.of("before", "phase", "service:PALETTE_FADE", "game-vblank", "fade",
                    "prepare:PALETTE_FADE", "after:false"), events);
            assertEquals(99, EngineTiming.vIntRunCounter().value(), "existing transition timing is retained");
            assertEquals(1, controller.after);
        } finally { blocking.close(); }
    }

    @Test void phaseQueryFailureHasNoNativeServiceAndNoAfterTick() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.NORMAL_PAUSE) {
            @Override public PlcLifecyclePhase heldVBlankPhase() { throw new IllegalStateException("phase"); }
        };
        load(controller);
        assertThrows(IllegalStateException.class, this::step);
        assertEquals(List.of("before"), events);
        assertEquals(99, EngineTiming.vIntRunCounter().value());
        assertEquals(0, controller.after);
    }

    @Test void failedServicePropagatesClosesItsTokenAndNeverFallsThroughOrCompletesTheTick() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.NORMAL_PAUSE);
        load(controller);
        var failure = new IllegalStateException("failed V-int");
        service.onService = () -> context.plcFrameLifecycle().afterConsumedIteration(
                () -> events.add("completed"));
        service.failure = failure;
        assertSame(failure, assertThrows(IllegalStateException.class, this::step));
        assertEquals(List.of("before", "phase", "fade", "service:NORMAL_PAUSE"), events);
        assertEquals(0, controller.after);
        assertFalse(events.contains("completed"), "failed services must discard success completions");
        assertEquals(100, EngineTiming.vIntRunCounter().value());
        assertEquals(41, context.getLevelManager().getFrameCounter());
        service.failure = null;
        events.clear();
        assertEquals(LevelFrameResult.HELD, step(), "failed token must not poison the next lifecycle");
        assertEquals(1, controller.after);
        assertEquals(101, EngineTiming.vIntRunCounter().value());
    }

    @Test void failedFadePreclaimOnHeldRowDiscardsCompletionAndKeepsTheFade() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.LAG);
        load(controller);
        context.plcFrameLifecycle().beginNativeBlockingFade();
        var failure = new IllegalStateException("held fade preclaim");
        service.onService = () -> context.plcFrameLifecycle().afterConsumedIteration(
                () -> events.add("failed-completion"));
        service.failure = failure;

        assertSame(failure, assertThrows(IllegalStateException.class, this::step));
        assertEquals(List.of("before", "phase", "service:PALETTE_FADE"), events);
        assertEquals(0, failure.getSuppressed().length);
        assertFailedHeldRow(controller);
        service.failure = null;
        service.onService = null;
        assertNextHeldRowRetainsFade(controller);
        assertFalse(events.contains("failed-completion"));
    }

    @Test void failedPreFadeGameVblankOnHeldRowRetiresTokenWithoutPreparing() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.NORMAL_PAUSE);
        load(controller);
        context.plcFrameLifecycle().beginNativeBlockingFade();
        var failure = new IllegalStateException("held game VBlank");
        profile.failure = failure;

        assertSame(failure, assertThrows(IllegalStateException.class, this::step));
        assertEquals(List.of("before", "phase", "service:PALETTE_FADE", "game-vblank"), events);
        assertMissingFadePreparation(failure);
        assertFailedHeldRow(controller);
        profile.failure = null;
        assertNextHeldRowRetainsFade(controller);
    }

    @Test void failedFadeUpdateOnHeldRowRetiresTokenWithoutPreparing() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.NORMAL_PAUSE);
        load(controller);
        context.plcFrameLifecycle().beginNativeBlockingFade();
        var failure = new IllegalStateException("held fade update");
        fade.failure = failure;

        assertSame(failure, assertThrows(IllegalStateException.class, this::step));
        assertEquals(List.of("before", "phase", "service:PALETTE_FADE", "game-vblank", "fade"), events);
        assertMissingFadePreparation(failure);
        assertFailedHeldRow(controller);
        fade.failure = null;
        assertNextHeldRowRetainsFade(controller);
    }

    @Test void failedFadePreparationOnHeldRowIsNotRetriedAndReleasesItsToken() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.LAG);
        load(controller);
        context.plcFrameLifecycle().beginNativeBlockingFade();
        var failure = new IllegalStateException("held fade preparation");
        service.preparationFailure = failure;

        assertSame(failure, assertThrows(IllegalStateException.class, this::step));
        assertEquals(List.of("before", "phase", "service:PALETTE_FADE", "game-vblank", "fade",
                "prepare:PALETTE_FADE"), events);
        assertMissingFadePreparation(failure);
        assertFailedHeldRow(controller);
        service.preparationFailure = null;
        assertNextHeldRowRetainsFade(controller);
    }

    private void assertFailedHeldRow(HeldDecision controller) {
        assertEquals(0, controller.after);
        assertEquals(100, EngineTiming.vIntRunCounter().value());
        assertEquals(41, context.getLevelManager().getFrameCounter());
        assertEquals(17, context.getSpriteManager().getFrameCounter());
        assertFalse(events.contains("after:false"));
        assertFalse(events.contains("boundary:VINT_SERVICE"));
    }

    private void assertNextHeldRowRetainsFade(HeldDecision controller) {
        // Only the injected failure is removed; no lifecycle reset or fade close.
        events.clear();
        assertEquals(LevelFrameResult.HELD, step());
        assertEquals(List.of("before", "phase", "service:PALETTE_FADE", "game-vblank", "fade",
                "prepare:PALETTE_FADE", "hardware-before:VINT_SERVICE", "hardware-after:VINT_SERVICE",
                "boundary:VINT_SERVICE", "after:false"), events);
        assertEquals(1, controller.after);
        assertEquals(101, EngineTiming.vIntRunCounter().value());
        assertEquals(41, context.getLevelManager().getFrameCounter());
        assertEquals(17, context.getSpriteManager().getFrameCounter());
    }

    private static void assertMissingFadePreparation(Throwable failure) {
        assertEquals(1, failure.getSuppressed().length);
        assertInstanceOf(IllegalStateException.class, failure.getSuppressed()[0]);
        assertEquals("missing PLC preparation for PALETTE_FADE", failure.getSuppressed()[0].getMessage());
    }

    @Test void failedHardwareBoundaryPropagatesWithoutAnAfterTickAndReleasesTheToken() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.LAG);
        load(controller);
        var failure = new IllegalStateException("hardware");
        context.setHardwareTimingBoundaryObserver(boundary -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, this::step));
        assertEquals(0, controller.after);
        assertFalse(events.contains("after:false"));
        assertTokenCompleted();
    }

    @Test void afterTickFailureDoesNotReplayOrLeaveTheCompletedNativeServiceOpen() {
        var failure = new IllegalStateException("after tick");
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.NORMAL_PAUSE) {
            @Override public void afterTick(CourseControl course, boolean advanced) {
                super.afterTick(course, advanced);
                throw failure;
            }
        };
        load(controller);
        assertSame(failure, assertThrows(IllegalStateException.class, this::step));
        assertEquals(1, controller.after);
        assertEquals(1, events.stream().filter("service:NORMAL_PAUSE"::equals).count());
        assertEquals(1, events.stream().filter("game-vblank"::equals).count());
        assertTokenCompleted();
    }

    @Test void nonHeldDispositionDoesNotQueryTheHeldOwner() {
        HeldDecision controller = new HeldDecision(PlcLifecyclePhase.ORDINARY_LEVEL);
        controller.disposition = GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
        load(controller);
        var admitted = new IllegalStateException("admitted body");
        assertSame(admitted, assertThrows(IllegalStateException.class, () -> ControlledFrameRuntime.step(
                context, new InputHandler(), LogicalInputSnapshot.neutral(), true, () -> { throw admitted; })));
        assertEquals(List.of("before"), events);
        assertEquals(0, controller.phaseReads);
        assertEquals(0, controller.after);
    }

    private void assertTokenCompleted() {
        // A fresh token is an observable API check; no private-field/source inspection.
        var next = context.plcFrameLifecycle().latchBeforeFadeUpdate();
        next.finish();
    }

    private class Decision implements GameplayFrameController {
        int after;
        GameplayFrameDisposition disposition = GameplayFrameDisposition.HOLD;
        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
            fail("disposition callback owns this row"); return false;
        }
        @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
            events.add("before"); return disposition;
        }
        @Override public void afterTick(CourseControl course, boolean advanced) {
            after++; events.add("after:" + advanced);
        }
    }

    private class HeldDecision extends Decision implements ControlledVBlankOnlyOwner {
        final PlcLifecyclePhase phase;
        int phaseReads;
        HeldDecision(PlcLifecyclePhase phase) { this.phase = phase; }
        @Override public PlcLifecyclePhase heldVBlankPhase() { phaseReads++; events.add("phase"); return phase; }
    }

    private final class RecordingService implements PlcLifecycleService {
        RuntimeException failure;
        Runnable onService;
        RuntimeException preparationFailure;
        long clockAtService;
        @Override public void serviceVBlank(PlcLifecyclePhase phase) {
            clockAtService = EngineTiming.vIntRunCounter().value();
            events.add("service:" + phase);
            if (onService != null) onService.run();
            if (failure != null) throw failure;
        }
        @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
            return phase == PlcLifecyclePhase.PALETTE_FADE;
        }
        @Override public void prepareAfterLoop(PlcLifecyclePhase phase) {
            events.add("prepare:" + phase);
            if (preparationFailure != null) throw preparationFailure;
        }
    }

    private final class RecordingProfile implements LevelInitProfile {
        boolean musicPending;
        RuntimeException failure;
        @Override public void serviceLevelLoadVBlank() {
            events.add("game-vblank");
            if (failure != null) throw failure;
        }
        @Override public boolean isLevelMusicPublicationPending() { return musicPending; }
        @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) { return List.of(); }
        @Override public List<InitStep> levelTeardownSteps() { return List.of(); }
        @Override public List<InitStep> perTestResetSteps() { return List.of(); }
        @Override public List<StaticFixup> postTeardownFixups() { return List.of(); }
    }

    private final class RecordingFade extends FadeManager {
        Runnable completion;
        RuntimeException failure;
        @Override public void update() {
            events.add("fade");
            if (failure != null) throw failure;
            super.update();
            Runnable callback = completion;
            completion = null;
            if (callback != null) callback.run();
        }
    }

    private static final class NativeProbe extends AbstractPlayableSprite implements NativePlayableSlot {
        NativeProbe() { super("main", (short) 0, (short) 0); }
        @Override public void runPlayableSlot(LevelManager manager, int frames) { fail("HOLD ran a native body"); }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() {
            groundSensors = new Sensor[0]; ceilingSensors = new Sensor[0]; pushSensors = new Sensor[0];
        }
        @Override public void draw() { fail("controlled stepping must not render"); }
    }
}
