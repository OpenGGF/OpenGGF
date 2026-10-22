package com.openggf.game.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.NoOpResultsScreen;
import com.openggf.game.ResultsScreen;
import com.openggf.game.SpecialStageAccessType;
import com.openggf.game.SpecialStageDebugProvider;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/** ROM-free ownership regressions using real session registries and replay cursors. */
class TestLiveRewindOwnerLifetime {
    private final List<GameplayModeContext> privateOwners = new ArrayList<>();
    private final AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.SPECIAL_STAGE);
    private SonicConfigurationService config;
    private AudioManager audio;
    private LiveRewindManager manager;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED, false);
        audio = EngineServices.current().audio();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        manager = new LiveRewindManager(config, audio, mode::get);
    }

    @AfterEach
    void tearDown() {
        for (GameplayModeContext owner : privateOwners.reversed()) {
            owner.destroy();
        }
        SessionManager.clear();
        audio.resetState();
        config.resetToDefaults();
    }

    @Test
    void installedOwnerReplaysItsRegisteredProviderWhileAnotherSessionIsAmbient() {
        GameplayModeContext ambient = TestEnvironment.activeGameplayMode();
        CountingStage ambientStage = registerStage(ambient, 40);
        GameplayModeContext owner = privateOwner();
        CountingStage stage = registerStage(owner, 0);
        InputHandler input = new InputHandler();

        bind(owner);
        record(stage, input, 3);
        RewindController controller = owner.getRewindController();
        assertNotNull(controller, "the installed owner receives the live history");
        assertNull(ambient.getRewindController(), "ambient session must not receive that history");

        controller.seekTo(1);

        assertEquals(1, controller.currentFrame());
        assertEquals(1, stage.ticks, "replay restores and advances the installed provider");
        assertEquals(40, ambientStage.ticks, "ambient provider is outside this history");
        assertSame(ambient, SessionManager.getCurrentGameplayMode());
    }

    @Test
    void parkingReleasesPresentationAndResumesTheSameHistoryWithoutAdvancingIt() {
        GameplayModeContext owner = privateOwner();
        CountingStage stage = registerStage(owner, 0);
        InputHandler input = new InputHandler();
        bind(owner);
        record(stage, input, 4);
        RewindController controller = owner.getRewindController();
        FadeManager fade = owner.getFadeManager();
        holdRewind(input);
        assertEquals(3, controller.currentFrame());
        assertTrue(fade.isReversePresentationActive());

        mode.set(GameMode.MASTER_TITLE_SCREEN);
        manager.bindOwner(null, true, GameMode.MASTER_TITLE_SCREEN);

        assertFalse(audio.isReverseAudioPresentationActive());
        assertFalse(fade.isReversePresentationActive());
        controller.seekTo(0);
        owner.getPlaybackController().stepForwardOnce();
        assertFalse(controller.recordExternalStep(), "parked history cannot record an external tick");
        assertEquals(3, controller.currentFrame(), "parked controller entries leave the cursor alone");
        assertEquals(3, stage.ticks);

        releaseKey(input);
        bind(owner);
        assertSame(controller, owner.getRewindController(), "resume preserves the parked segment");
        record(stage, input, 1);
        assertEquals(4, controller.currentFrame());
        controller.seekTo(1);
        assertEquals(1, stage.ticks, "the pre-park past is still available after resume");
    }

    @Test
    void destroyedOutgoingOwnerStillOwnsRepeatedReleaseRetries() {
        GameplayModeContext outgoing = privateOwner();
        CountingStage stage = registerStage(outgoing, 0);
        GameplayModeContext incoming = privateOwner();
        registerStage(incoming, 20);
        InputHandler outgoingInput = new InputHandler();
        bind(outgoing);
        record(stage, outgoingInput, 4);
        holdRewind(outgoingInput);

        AudioManagerTestDiagnostics.failNextReverseRelease(audio);
        bind(incoming);
        assertTrue(manager.isRewindingOrReleasing());
        assertNull(incoming.getRewindController(), "replacement waits for the outgoing release");
        outgoing.destroy();
        assertNull(outgoing.getFadeManager(), "teardown really cleared the outgoing context");
        assertNull(outgoing.getRewindController());

        FadeManager incomingFade = incoming.getFadeManager();
        incomingFade.beginReversePresentation();
        var prepared = AudioManagerTestDiagnostics.producerFingerprint(audio);
        for (int retry = 0; retry < 2; retry++) {
            AudioManagerTestDiagnostics.failNextReverseRelease(audio);
            assertTrue(manager.retryPendingRelease(), "failed release keeps consuming host time");
            assertEquals(prepared, AudioManagerTestDiagnostics.producerFingerprint(audio),
                    "retry must retain the prepared outgoing audio transaction");
            assertTrue(incomingFade.isReversePresentationActive(),
                    "cleanup cannot end the replacement owner's presentation");
            assertNull(incoming.getRewindController());
        }

        assertFalse(manager.retryPendingRelease());
        assertFalse(audio.isReverseAudioPresentationActive());
        assertFalse(manager.isRewindingOrReleasing());
        assertTrue(incomingFade.isReversePresentationActive());
        incomingFade.endReversePresentation();
        bind(incoming);
        assertNotNull(incoming.getRewindController(), "the replacement installs after release commits");
    }

    @Test
    void aSecondReplacementSupersedesTheWaitingOwnerWithoutLosingOutgoingCleanup() {
        GameplayModeContext first = privateOwner();
        CountingStage firstStage = registerStage(first, 0);
        GameplayModeContext middle = privateOwner();
        registerStage(middle, 20);
        GameplayModeContext latest = privateOwner();
        CountingStage latestStage = registerStage(latest, 40);
        InputHandler input = new InputHandler();
        bind(first);
        record(firstStage, input, 4);
        RewindController outgoingController = first.getRewindController();
        FadeManager outgoingFade = first.getFadeManager();
        holdRewind(input);

        AudioManagerTestDiagnostics.failNextReverseRelease(audio);
        bind(middle);
        bind(latest);

        assertNull(middle.getRewindController());
        assertNull(latest.getRewindController());
        assertTrue(outgoingFade.isReversePresentationActive());
        outgoingController.seekTo(0);
        assertEquals(3, outgoingController.currentFrame(), "retired history cannot seek while release waits");
        AudioManagerTestDiagnostics.failNextReverseRelease(audio);
        assertTrue(manager.retryPendingRelease());
        assertFalse(manager.retryPendingRelease());
        assertFalse(audio.isReverseAudioPresentationActive());
        assertFalse(outgoingFade.isReversePresentationActive(), "release still belongs to the first owner");

        bind(latest);
        record(latestStage, new InputHandler(), 1);
        assertNull(middle.getRewindController(), "an obsolete waiting owner must never install");
        assertEquals(1, latest.getRewindController().currentFrame());
        assertEquals(41, latestStage.ticks);
    }

    @Test
    void staleBoundaryFromAnEarlierInstallationCannotRerootTheSameOwnersNewHistory() {
        GameplayModeContext owner = privateOwner();
        CountingStage stage = registerStage(owner, 0);
        GameplayModeContext other = privateOwner();
        registerStage(other, 20);
        InputHandler input = new InputHandler();
        bind(owner);
        record(stage, input, 3);
        RewindBoundaryReporter stale = manager.boundaryReporterFor(owner);
        RewindController oldController = owner.getRewindController();
        bind(other);
        bind(owner);
        record(stage, input, 2);
        RewindController current = owner.getRewindController();
        assertNotSame(oldController, current);
        assertEquals(2, current.currentFrame());

        stale.markBoundary(RewindBoundary.LEVEL_LOAD);

        assertEquals(2, current.currentFrame(), "owner identity alone cannot authorize an old callback");
        manager.boundaryReporterFor(owner).markBoundary(RewindBoundary.LEVEL_LOAD);
        assertEquals(0, current.currentFrame(), "the current installation still accepts its own boundary");
    }

    @Test
    void replacingTheRegisteredSpecialProviderStartsANewSegmentAndRetiresOldPlayback() {
        GameplayModeContext owner = privateOwner();
        CountingStage first = registerStage(owner, 0);
        InputHandler input = new InputHandler();
        bind(owner);
        record(first, input, 3);
        RewindController oldController = owner.getRewindController();
        PlaybackController oldPlayback = owner.getPlaybackController();
        CountingStage replacement = registerStage(owner, 50);

        bind(owner);

        RewindController current = owner.getRewindController();
        assertNotSame(oldController, current, "one owner may host distinct special-stage lifetimes");
        assertEquals(0, current.currentFrame());
        oldController.seekTo(0);
        oldPlayback.stepBackwardOnce();
        assertEquals(3, oldController.currentFrame());
        assertEquals(50, replacement.ticks, "retired history cannot restore into the new provider");
        record(replacement, input, 1);
        current.seekTo(0);
        assertEquals(50, replacement.ticks, "new frame zero captures the replacement provider");
        owner.getPlaybackController().stepForwardOnce();
        assertEquals(51, replacement.ticks, "new replay advances that exact registered provider");
        assertEquals(3, first.ticks);
    }

    @Test
    void restoreBoundaryWaitsForTheAdmittedSeekThenRerootsAtTheNextManagerEntry() {
        GameplayModeContext owner = privateOwner();
        CountingStage stage = registerStage(owner, 0);
        InputHandler input = new InputHandler();
        bind(owner);
        record(stage, input, 4);
        RewindController controller = owner.getRewindController();
        stage.afterRestore = () -> owner.markRewindBoundary(RewindBoundary.LEVEL_LOAD);

        controller.seekTo(2);

        assertEquals(2, controller.currentFrame(), "the admitted seek completes before a boundary reroot");
        assertEquals(2, stage.ticks);
        assertFalse(controller.stepBackward(), "a deferred boundary denies the next history operation");
        bind(owner);
        assertSame(controller, owner.getRewindController());
        assertEquals(0, controller.currentFrame());
        assertEquals(2, stage.ticks, "frame zero captures the completed seek, not its intermediate restore");
    }

    @Test
    void aDeferredBoundaryCannotAcquireAuthorityOverAReplacementInstallation() {
        GameplayModeContext owner = privateOwner();
        CountingStage stage = registerStage(owner, 0);
        GameplayModeContext replacement = privateOwner();
        CountingStage replacementStage = registerStage(replacement, 30);
        InputHandler input = new InputHandler();
        bind(owner);
        record(stage, input, 4);
        RewindController controller = owner.getRewindController();
        stage.afterRestore = () -> {
            owner.markRewindBoundary(RewindBoundary.LEVEL_LOAD);
            bind(replacement);
        };

        controller.seekTo(2);
        bind(replacement);
        record(replacementStage, input, 2);

        assertEquals(2, replacement.getRewindController().currentFrame());
        assertEquals(32, replacementStage.ticks);
        assertEquals(2, controller.currentFrame(), "outgoing seek completed before retirement");
        assertFalse(controller.stepBackward());
    }

    @Test
    void recordingAnExecutedOutgoingTickCannotAppendToTheNewOwnersHistory() {
        GameplayModeContext outgoing = privateOwner();
        CountingStage oldStage = registerStage(outgoing, 0);
        GameplayModeContext incoming = privateOwner();
        CountingStage newStage = registerStage(incoming, 30);
        InputHandler input = new InputHandler();
        bind(outgoing);
        record(oldStage, input, 2);
        oldStage.update();
        bind(incoming); // a completion callback changed the binding before the recorder tail

        manager.recordExternalFrame(outgoing, GameMode.SPECIAL_STAGE, oldStage, false, input, false);

        assertEquals(0, incoming.getRewindController().currentFrame());
        assertEquals(30, newStage.ticks);
        record(newStage, input, 1);
        assertEquals(1, incoming.getRewindController().currentFrame());
    }

    @Test
    void aBonusTickCannotRecordTheProviderInstalledByItsCompletionCallback() {
        mode.set(GameMode.BONUS_STAGE);
        GameplayModeContext owner = privateOwner();
        var first = org.mockito.Mockito.mock(com.openggf.game.BonusStageProvider.class);
        var replacement = org.mockito.Mockito.mock(com.openggf.game.BonusStageProvider.class);
        owner.setActiveBonusStageProvider(first);
        manager.bindOwner(owner, false, GameMode.BONUS_STAGE);
        InputHandler input = new InputHandler();
        manager.recordExternalFrame(owner, GameMode.BONUS_STAGE, null, first, false, input, false);
        RewindController controller = owner.getRewindController();
        assertEquals(1, controller.currentFrame());
        owner.setActiveBonusStageProvider(replacement);

        manager.recordExternalFrame(owner, GameMode.BONUS_STAGE, null, first, false, input, false);

        assertEquals(1, controller.currentFrame(), "outgoing tick must not capture the replacement provider");
        manager.recordExternalFrame(owner, GameMode.BONUS_STAGE, null, replacement, false, input, false);
        assertEquals(2, controller.currentFrame());
    }

    @Test
    void auditorRestoreBoundaryIsAppliedAfterTheCompletedExternalRecord() {
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT, true);
        GameplayModeContext owner = privateOwner();
        CountingStage stage = registerStage(owner, 0);
        InputHandler input = new InputHandler();
        bind(owner);
        RewindController controller = owner.getRewindController();
        List<String> reports = new ArrayList<>();
        RewindDeterminismAuditor auditor = new RewindDeterminismAuditor(reports::add);
        controller.setDeterminismAuditor(auditor);
        // Forward rows must service the same native PLC/hardware boundaries as
        // replay. Advancing only the fake provider manufactures diagnostic drift.
        LiveRewindInputSource forwardInputs = new LiveRewindInputSource();
        SpecialStageStepper forward = new SpecialStageStepper(forwardInputs, () -> input, stage, owner);
        for (int row = 1; row <= 60; row++) {
            forwardInputs.appendFrame(input, config);
            forward.step(forwardInputs.read(row));
            if (row == 60) {
                stage.afterRestore = () -> owner.markRewindBoundary(RewindBoundary.LEVEL_LOAD);
            }
            manager.recordExternalFrame(GameMode.SPECIAL_STAGE, false, input);
        }

        assertEquals(0, auditor.divergentSegmentCount(), () -> String.join("\n", reports));
        assertSame(controller, owner.getRewindController());
        assertEquals(0, controller.currentFrame(), "the audit's callback reroots only after recording finishes");
        assertEquals(60, stage.ticks, "audit restores completed live state before the deferred boundary captures it");
    }

    @Test
    void deferredRecordAndFloorCannotAlterAReplacementInstallation() {
        GameplayModeContext owner = privateOwner();
        CountingStage stage = registerStage(owner, 0);
        InputHandler input = new InputHandler();
        bind(owner);
        RewindController outgoing = owner.getRewindController();
        owner.plcFrameLifecycle().runLogicalIteration(() -> { }, frame -> {
            frame.claim(com.openggf.game.resources.PlcLifecyclePhase.SPECIAL_STAGE);
            stage.update();
            manager.recordExternalFrame(owner, GameMode.SPECIAL_STAGE, stage, false, input, false);
            manager.markBoundary(RewindBoundary.LEVEL_LOAD);
            assertEquals(0, outgoing.currentFrame(), "recording waits for successful publication");
            CountingStage replacement = registerStage(owner, 30);
            bind(owner);
            replacement.update();
            manager.recordExternalFrame(owner, GameMode.SPECIAL_STAGE, replacement, false, input, false);
            return null;
        });
        assertNotSame(outgoing, owner.getRewindController());
        assertEquals(0, outgoing.currentFrame());
        assertEquals(1, owner.getRewindController().currentFrame(),
                "old callbacks cannot reset or append to the new installation");
    }

    @Test
    void failedIterationCannotRecordAndDoesNotCarryItsInputIntoTheNextFrame() {
        GameplayModeContext owner = privateOwner();
        CountingStage stage = registerStage(owner, 0);
        InputHandler input = new InputHandler();
        bind(owner);
        RewindController controller = owner.getRewindController();
        assertThrows(IllegalStateException.class, () ->
                owner.plcFrameLifecycle().runLogicalIteration(() -> { }, frame -> {
                    frame.claim(com.openggf.game.resources.PlcLifecyclePhase.SPECIAL_STAGE);
                    stage.update();
                    manager.recordExternalFrame(owner, GameMode.SPECIAL_STAGE, stage, false, input, false);
                    throw new IllegalStateException("failed tick");
                }));
        assertEquals(0, controller.currentFrame());
        record(stage, input, 1);
        assertEquals(1, controller.currentFrame());
    }

    @Test
    void inactiveLevelBindingDoesNotCaptureBeforeAdmissionAndActiveFadeTicksStillRecord() {
        mode.set(GameMode.LEVEL);
        GameplayModeContext owner = privateOwner();
        var captures = new java.util.concurrent.atomic.AtomicInteger();
        var callbacks = new java.util.concurrent.atomic.AtomicInteger();
        owner.getRewindRegistry().register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "test-inactive-binding"; }
            @Override public Integer capture() { return captures.incrementAndGet(); }
            @Override public void restore(Integer state) { }
        });
        owner.getLevelManager().setLevelInactiveForTransition(true);
        owner.getFadeManager().startFadeToBlack(callbacks::incrementAndGet);
        InputHandler input = new InputHandler();

        manager.bindOwner(owner, false, GameMode.LEVEL);
        assertNull(owner.getRewindController(), "binding an inactive source cannot capture a history floor");
        manager.bindOwner(owner, false, GameMode.LEVEL);
        assertFalse(manager.handleRealtimeRewindInput(GameMode.LEVEL, true, input));
        assertEquals(0, captures.get());
        assertEquals(0, callbacks.get());
        assertTrue(owner.getFadeManager().hasPendingCompletion());

        owner.getLevelManager().setLevelInactiveForTransition(false);
        manager.bindOwner(owner, false, GameMode.LEVEL);
        RewindController controller = owner.getRewindController();
        assertNotNull(controller, "valid binding still captures an immediate frame-zero floor");
        assertEquals(0, controller.currentFrame());
        owner.markRewindBoundary(RewindBoundary.MODE_ENTER_REWINDABLE);
        assertSame(controller, owner.getRewindController());
        manager.recordExternalFrame(owner, GameMode.LEVEL, null, false, input, false);
        assertNotNull(owner.getRewindController(), "an admitted completed cosmetic-fade tick still records");
        assertEquals(1, owner.getRewindController().currentFrame());
        owner.markRewindBoundary(RewindBoundary.LEVEL_LOAD);
        assertSame(controller, owner.getRewindController());
        assertEquals(0, controller.currentFrame(), "explicit load bootstrap still resets the admitted floor");
        assertTrue(owner.getFadeManager().hasPendingCompletion());
        assertEquals(0, callbacks.get());
    }

    @Test
    void registeredSpecialStageStillInstallsWhileItsOutgoingLevelIsInactive() {
        GameplayModeContext owner = privateOwner();
        CountingStage stage = registerStage(owner, 0);
        owner.getLevelManager().setLevelInactiveForTransition(true);
        bind(owner);
        RewindController controller = owner.getRewindController();
        assertNotNull(controller);
        assertEquals(0, controller.currentFrame());
        owner.markRewindBoundary(RewindBoundary.MODE_ENTER_REWINDABLE);
        record(stage, new InputHandler(), 2);
        assertSame(controller, owner.getRewindController());
        controller.seekTo(0);
        assertEquals(0, stage.ticks);
    }

    @Test
    void scriptedRewindIsResolvedFromTheInstalledOwnersModuleWhileTheLiveKeyIsDisabled() {
        mode.set(GameMode.LEVEL);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        GameplayModeContext ambient = TestEnvironment.activeGameplayMode();
        ScriptedRewind script = org.mockito.Mockito.mock(ScriptedRewind.class);
        var module = new com.openggf.game.patch.DelegatingGameModule(
                GameModuleRegistry.getCurrent(), "test:installed-rewind-script") {
            @Override public ScriptedRewind scriptedRewind() { return script; }
        };
        GameplayModeContext owner = privateOwner(module);

        manager.bindOwner(owner, false, GameMode.LEVEL);
        assertFalse(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, new InputHandler()));

        assertNotNull(owner.getRewindController(), "the owner's script enables its own history");
        assertNull(ambient.getRewindController());
        org.mockito.Mockito.verify(script).requested();
        assertSame(ambient, SessionManager.getCurrentGameplayMode());
    }

    private GameplayModeContext privateOwner() {
        return privateOwner(GameModuleRegistry.getCurrent());
    }

    private GameplayModeContext privateOwner(com.openggf.game.GameModule module) {
        GameplayModeContext owner = new GameplayModeContext(new WorldSession(module));
        GameplaySessionFactory.attachManagers(owner, EngineServices.current());
        privateOwners.add(owner);
        return owner;
    }

    private CountingStage registerStage(GameplayModeContext owner, int ticks) {
        CountingStage stage = new CountingStage(ticks);
        owner.registerSpecialStageAdapter(stage);
        return stage;
    }

    private void bind(GameplayModeContext owner) {
        mode.set(GameMode.SPECIAL_STAGE);
        manager.bindOwner(owner, false, GameMode.SPECIAL_STAGE);
    }

    private void record(CountingStage stage, InputHandler input, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            stage.update();
            manager.recordExternalFrame(GameMode.SPECIAL_STAGE, false, input);
        }
    }

    private void holdRewind(InputHandler input) {
        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_PRESS);
        assertTrue(manager.handleRealtimeRewindInput(GameMode.SPECIAL_STAGE, false, input));
    }

    private void releaseKey(InputHandler input) {
        input.handleKeyEvent(config.getInt(SonicConfiguration.LIVE_REWIND_KEY), GLFW_RELEASE);
    }

    /** Its integer is gameplay state, captured by the same registry as the real providers. */
    private static final class CountingStage implements SpecialStageProvider {
        private int ticks;
        private Runnable afterRestore;
        private final RewindSnapshottable<Integer> adapter = new RewindSnapshottable<>() {
            @Override public String key() { return SPECIAL_STAGE_REWIND_KEY; }
            @Override public Integer capture() { return ticks; }
            @Override public void restore(Integer snapshot) {
                ticks = snapshot;
                Runnable callback = afterRestore;
                afterRestore = null;
                if (callback != null) callback.run();
            }
        };

        private CountingStage(int ticks) { this.ticks = ticks; }
        @Override public boolean supportsRewind() { return true; }
        @Override public Optional<RewindSnapshottable<?>> rewindAdapter() { return Optional.of(adapter); }
        @Override public void update() { ticks++; }
        @Override public boolean hasSpecialStages() { return true; }
        @Override public SpecialStageAccessType getAccessType() { return SpecialStageAccessType.GIANT_RING; }
        @Override public void initializeStage(int stageIndex) { }
        @Override public int getCurrentStage() { return 0; }
        @Override public boolean isEmeraldCollected() { return false; }
        @Override public int getEmeraldIndex() { return -1; }
        @Override public int getRingsCollected() { return 0; }
        @Override public void setEmeraldCollected(boolean collected) { }
        @Override public boolean isSpriteDebugMode() { return false; }
        @Override public void toggleSpriteDebugMode() { }
        @Override public void cyclePlaneDebugMode() { }
        @Override public SpecialStageDebugProvider getDebugProvider() { return null; }
        @Override public boolean isAlignmentTestMode() { return false; }
        @Override public void toggleAlignmentTestMode() { }
        @Override public void adjustAlignmentOffset(int delta) { }
        @Override public void adjustAlignmentSpeed(double delta) { }
        @Override public void toggleAlignmentStepMode() { }
        @Override public void renderAlignmentOverlay(int viewportWidth, int viewportHeight) { }
        @Override public void renderLagCompensationOverlay(int viewportWidth, int viewportHeight) { }
        @Override public void setLagCompensation(double factor) { }
        @Override public ResultsScreen createResultsScreen(int ringsCollected, boolean gotEmerald,
                                                          int stageIndex, int totalEmeraldCount) {
            return NoOpResultsScreen.INSTANCE;
        }
        @Override public void initialize() { }
        @Override public void draw() { }
        @Override public void handleInput(int heldButtons, int pressedButtons) { }
        @Override public boolean isFinished() { return false; }
        @Override public void reset() { ticks = 0; }
        @Override public boolean isInitialized() { return true; }
    }
}
