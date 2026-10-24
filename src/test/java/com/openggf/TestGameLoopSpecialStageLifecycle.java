package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.*;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.*;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectCallbackAbortException;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.SpecialStageLifecycleTestAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Preservation controls first run on unchanged a79e3295a production. */
@Isolated
class TestGameLoopSpecialStageLifecycle {
    private GameLoop loop;
    private EngineContext engine;
    private GameModule module;
    private SpecialStageProvider provider;
    private AudioManager audio;
    private LevelManager levels;
    private SpriteManager sprites;
    private FadeManager fade;
    private Camera camera;
    private InputHandler input;
    private TitleCardProvider title;
    private GameplayModeContext context;

    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.resetPerTest(); SessionManager.clear();
        module = mock(GameModule.class);
        when(module.getRules()).thenReturn(GameRules.SONIC_2);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);
        when(module.createRuntimeArtCoordinator(any())).thenReturn(RuntimeArtCoordinator.NONE);
        SessionManager.openGameplaySession(module); context = TestEnvironment.activeGameplayMode();
        audio = mock(AudioManager.class);
        var old = EngineServices.current();
        engine = new EngineContext(old.configuration(), old.graphics(), audio, old.roms(), old.profiler(),
                old.debugOverlay(), old.playbackDebug(), old.romDetection(), old.crossGameFeatures(), old.moduleResolutionService());
        input = mock(InputHandler.class); when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        loop = new GameLoop(engine, input);
        levels = mock(LevelManager.class); sprites = mock(SpriteManager.class); fade = mock(FadeManager.class);
        camera = mock(Camera.class); title = mock(TitleCardProvider.class);
        write(loop, "levelManager", levels); write(loop, "spriteManager", sprites); write(loop, "camera", camera);
        write(loop, "fadeManager", fade); write(loop, "titleCardProvider", title);
        provider = mock(SpecialStageProvider.class); when(provider.hasSpecialStages()).thenReturn(true);
        when(module.getSpecialStageProvider()).thenReturn(provider);
        when(sprites.getAllSprites()).thenReturn(List.of());
        clearInvocations(module, audio);
    }
    @AfterEach void cleanup() { SessionManager.clear(); }
    private void mode(GameMode value) { write(loop, "currentGameMode", value); }
    private void retained(SpecialStageProvider value) { write(loop, "activeSpecialStageProvider", value); }
    private void enterResults(boolean won) throws Exception { invoke(loop, "enterResultsScreen", new Class<?>[]{boolean.class}, won); }
    private void iteration() throws Exception { invoke(loop, "runSpecialStageResultsIteration", new Class<?>[0]); }
    private void stage() { mode(GameMode.SPECIAL_STAGE); retained(provider); }
    private void immediate() { when(fade.getState()).thenReturn(FadeManager.FadeState.HOLD_WHITE); }

    @Test void constructorDoesNotSampleProvidersOrPresentation() {
        clearInvocations(module, audio, provider);
        new GameLoop(engine, input);
        verify(module, never()).getSpecialStageProvider(); verifyNoInteractions(audio, provider);
    }
    @Test void ownerConstructionPerformsNoHostReadsOrValidation() {
        var host = mock(GameLoopSpecialStageLifecycle.Host.class);
        new GameLoopSpecialStageLifecycle(host, audio);
        new GameLoopSpecialStageLifecycle(null, null);
        verifyNoInteractions(host, audio);
    }
    @Test void outsideLookupResolvesFactoryEveryTimeAndInsideRetainsNullFallback() {
        var next = mock(SpecialStageProvider.class);
        when(module.getSpecialStageProvider()).thenReturn(provider, next);
        assertSame(provider, loop.getActiveSpecialStageProvider()); assertSame(next, loop.getActiveSpecialStageProvider());
        stage(); assertSame(provider, loop.getActiveSpecialStageProvider());
        mode(GameMode.SPECIAL_STAGE_RESULTS); assertSame(provider, loop.getActiveSpecialStageProvider());
        retained(null); assertSame(NoOpSpecialStageProvider.INSTANCE, loop.getActiveSpecialStageProvider());
        verify(module, times(2)).getSpecialStageProvider();
        mode(GameMode.LEVEL); assertSame(next, loop.getActiveSpecialStageProvider());
    }
    @Test void outsideLookupObservesModuleReplacement() {
        var replacement = mock(GameModule.class); var next = mock(SpecialStageProvider.class);
        when(replacement.getSpecialStageProvider()).thenReturn(next);
        try (var services = mockStatic(GameServices.class, CALLS_REAL_METHODS)) {
            services.when(GameServices::module).thenReturn(module, replacement);
            assertSame(provider, loop.getActiveSpecialStageProvider()); assertSame(next, loop.getActiveSpecialStageProvider());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void immediateAndDelayedResultsReachVirtualCompatibilityDelegate(boolean immediate) throws Exception {
        int[] calls = {0};
        loop = new GameLoop(engine, input) { @Override void doEnterResultsScreen() { calls[0]++; } };
        write(loop, "fadeManager", fade); stage();
        when(provider.getRingsCollected()).thenReturn(91); when(provider.getCurrentStage()).thenReturn(4);
        var callback = new AtomicReference<Runnable>();
        doAnswer(c -> { callback.set(c.getArgument(0)); return null; }).when(fade).startFadeToWhite(any());
        if (immediate) immediate();
        enterResults(false);
        assertEquals(91, read(loop, "ssRingsCollected")); assertEquals(4, read(loop, "ssStageIndex"));
        assertEquals(immediate ? 1 : 0, calls[0]);
        if (!immediate) { assertNotNull(callback.get()); callback.get().run(); assertEquals(1, calls[0]); }
        verify(provider, never()).resetForResults();
    }
    @Test void genuineFactoryDispatchCapturesRewardBeforeResetAndKeepsIdentity() throws Exception {
        stage(); immediate(); when(provider.getRingsCollected()).thenReturn(73); when(provider.getCurrentStage()).thenReturn(5);
        var screen = mock(ResultsScreen.class);
        when(provider.createResultsScreen(eq(73), eq(true), eq(5), anyInt())).thenAnswer(c -> {
            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode());
            assertEquals(0, read(loop, "resultsFrameCounter")); return screen;
        });
        doAnswer(c -> { assertEquals(73, read(loop, "ssRingsCollected")); assertEquals(true, read(loop, "ssEmeraldCollected")); return null; }).when(provider).resetForResults();
        enterResults(true);
        var order = inOrder(provider, fade);
        order.verify(provider).getRingsCollected(); order.verify(provider).getCurrentStage();
        order.verify(provider).publishEmeraldReward(any(), eq(5), eq(EmeraldRewardKind.CHAOS_EMERALD));
        order.verify(provider).resetForResults(); order.verify(provider).createResultsScreen(eq(73), eq(true), eq(5), anyInt());
        order.verify(fade).clearOverlayForImmediatePaletteLoad();
        assertSame(screen, loop.getResultsScreen()); assertEquals(5, loop.recordedSpecialStageIdentity(GameMode.SPECIAL_STAGE_RESULTS));
        when(provider.getCurrentStage()).thenReturn(0);
        assertEquals(5, loop.recordedSpecialStageIdentity(GameMode.SPECIAL_STAGE_RESULTS));
        enterResults(true); verify(provider, times(1)).publishEmeraldReward(any(), anyInt(), any());
    }
    @Test void factoryNullFallbackAndResultsGetterRemainVirtual() {
        stage(); loop.doEnterResultsScreen(); assertSame(NoOpResultsScreen.INSTANCE, loop.getResultsScreen());
        var screen = mock(ResultsScreen.class);
        GameLoop override = new GameLoop(engine, input) { @Override public ResultsScreen getResultsScreen() { return screen; } };
        assertSame(screen, override.getResultsScreen());
    }
    @Test void resultResetFailurePreservesCapturedOutcomeAndUnpublishedMode() throws Exception {
        stage(); immediate(); when(provider.getRingsCollected()).thenReturn(44); when(provider.getCurrentStage()).thenReturn(3);
        var error = new IllegalStateException("reset"); doThrow(error).when(provider).resetForResults();
        assertSame(error, assertThrows(IllegalStateException.class, () -> enterResults(false)));
        assertEquals(44, read(loop, "ssRingsCollected")); assertEquals(3, read(loop, "ssStageIndex"));
        assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode()); assertNull(loop.getResultsScreen());
    }
    @Test void factoryFailureLeavesModeAndCounterButPreviousScreenAssignment() {
        stage(); var old = mock(ResultsScreen.class); write(loop, "resultsScreen", old); write(loop, "resultsFrameCounter", 20);
        var error = new IllegalStateException("factory"); when(provider.createResultsScreen(anyInt(), anyBoolean(), anyInt(), anyInt())).thenThrow(error);
        assertSame(error, assertThrows(IllegalStateException.class, loop::doEnterResultsScreen));
        assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode()); assertEquals(0, read(loop, "resultsFrameCounter"));
        assertSame(old, loop.getResultsScreen()); verify(fade, never()).clearOverlayForImmediatePaletteLoad();
    }
    @Test void resultsListenerRebindsLiveFadeAfterCapturedFactoryAndMusic() {
        stage(); var rebound = mock(FadeManager.class); var screen = mock(ResultsScreen.class);
        when(provider.createResultsScreen(anyInt(), anyBoolean(), anyInt(), anyInt())).thenReturn(screen);
        loop.setGameModeChangeListener((old, now) -> { assertSame(screen, loop.getResultsScreen()); write(loop, "fadeManager", rebound); });
        loop.doEnterResultsScreen(); verify(rebound).clearOverlayForImmediatePaletteLoad(); verify(fade, never()).clearOverlayForImmediatePaletteLoad();
    }
    @Test void resultsListenerFailureLeavesPublishedScreenAndOverlay() {
        stage(); var screen = mock(ResultsScreen.class); var error = new IllegalArgumentException("listener");
        when(provider.createResultsScreen(anyInt(), anyBoolean(), anyInt(), anyInt())).thenReturn(screen);
        loop.setGameModeChangeListener((old, now) -> { throw error; });
        assertSame(error, assertThrows(IllegalArgumentException.class, loop::doEnterResultsScreen));
        assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode()); assertSame(screen, loop.getResultsScreen());
        verify(fade, never()).clearOverlayForImmediatePaletteLoad();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void resultsUpdateRereadsReboundScreenIncludingNullFailure(boolean toNull) throws Exception {
        mode(GameMode.SPECIAL_STAGE_RESULTS); var first = mock(ResultsScreen.class); var later = mock(ResultsScreen.class);
        write(loop, "resultsScreen", first); when(later.isComplete()).thenReturn(false);
        doAnswer(c -> { write(loop, "resultsScreen", toNull ? null : later); return null; }).when(first).update(eq(1), isNull());
        if (toNull) assertThrows(NullPointerException.class, this::iteration); else { iteration(); verify(later).isComplete(); }
        verify(first, never()).isComplete(); assertEquals(1, read(loop, "resultsFrameCounter"));
    }
    @Test void entryInitializeAndRegisterBeforeModeListenerAndLagSeam() throws Exception {
        doAnswer(c -> { assertSame(NoOpSpecialStageProvider.INSTANCE, read(loop, "activeSpecialStageProvider")); assertEquals(GameMode.LEVEL, loop.getCurrentGameMode()); return null; })
                .when(provider).initializeStage(2, SpecialStageStartupPolicy.TRACE_ACCURATE);
        loop.setGameModeChangeListener((old, now) -> { assertSame(provider, loop.getActiveSpecialStageProvider()); assertEquals(GameMode.SPECIAL_STAGE, now); });
        loop.doEnterSpecialStage(provider, 2, false, SpecialStageStartupPolicy.TRACE_ACCURATE);
        var order = inOrder(provider, fade); order.verify(provider).reset(); order.verify(provider).initializeStage(2, SpecialStageStartupPolicy.TRACE_ACCURATE);
        order.verify(fade).startFadeToWhite(isNull(), eq(Integer.MAX_VALUE)); order.verify(fade).deferFirstStepToNextVint();
        verify(provider, never()).initializeStage(anyInt(), any(), any());
        verify(provider, never()).setLagCompensation(anyInt());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void entryIoAndRuntimeCleanInOrderWithoutModeRollback(boolean io) throws Exception {
        var error = new IllegalStateException("entry"); var failure = new IOException("entry");
        if (io) doThrow(failure).when(provider).initializeStage(1, SpecialStageStartupPolicy.FAST);
        else doThrow(error).when(provider).initializeStage(1, SpecialStageStartupPolicy.FAST);
        write(loop, "specialStageTransitionPending", true);
        RuntimeException caught = assertThrows(RuntimeException.class, () -> loop.doEnterSpecialStage(provider, 1, false, SpecialStageStartupPolicy.FAST));
        if (io) { assertEquals("Failed to initialize Special Stage 2", caught.getMessage()); assertSame(failure, caught.getCause()); } else assertSame(error, caught);
        assertFalse((boolean)read(loop, "specialStageTransitionPending")); assertSame(NoOpSpecialStageProvider.INSTANCE, read(loop, "activeSpecialStageProvider"));
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
    }
    @Test void entryErrorIsUncaughtAndCallbackAbortRethrownByIdentity() throws Exception {
        var fatal = new AssertionError("fatal"); doThrow(fatal).when(provider).initializeStage(1, SpecialStageStartupPolicy.FAST);
        retained(provider); assertSame(fatal, assertThrows(AssertionError.class, () -> loop.doEnterSpecialStage(provider, 1, false, SpecialStageStartupPolicy.FAST)));
        assertSame(provider, read(loop, "activeSpecialStageProvider"));
        var abort = new ObjectCallbackAbortException("abort", null) { };
        doThrow(abort).when(provider).initializeStage(1, SpecialStageStartupPolicy.FAST);
        assertSame(abort, assertThrows(ObjectCallbackAbortException.class, () -> loop.doEnterSpecialStage(provider, 1, false, SpecialStageStartupPolicy.FAST)));
        assertSame(NoOpSpecialStageProvider.INSTANCE, read(loop, "activeSpecialStageProvider"));
    }
    @Test void listenerFailureLeavesSpecialModeButRetiresProvider() {
        var failure = new IllegalStateException("listener"); loop.setGameModeChangeListener((old, now) -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> loop.doEnterSpecialStage(provider, 0, false)));
        assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode()); assertSame(NoOpSpecialStageProvider.INSTANCE, loop.getActiveSpecialStageProvider());
    }
    @Test void cleanupFailureReplacesOriginalAndInterceptsProviderRetirement() throws Exception {
        var bound = mock(GameplayModeContext.class); var original = new IOException("initialize"); var cleanup = new IllegalStateException("cleanup");
        write(loop, "gameplayMode", bound); retained(provider);
        doThrow(original).when(provider).initializeStage(0, SpecialStageStartupPolicy.FAST);
        doThrow(cleanup).when(bound).deregisterSpecialStageAdapter();
        assertSame(cleanup, assertThrows(IllegalStateException.class, () -> loop.doEnterSpecialStage(provider, 0, false, SpecialStageStartupPolicy.FAST)));
        assertSame(provider, read(loop, "activeSpecialStageProvider"));
    }
    @Test void publicProviderOverrideDrivesInputButRewindUsesRetainedProvider() throws Exception {
        var other = mock(SpecialStageProvider.class);
        loop = new GameLoop(engine, input) { @Override public SpecialStageProvider getActiveSpecialStageProvider() { return other; } };
        stage(); invoke(loop, "updateSpecialStageInput", new Class<?>[0]); verify(other).handleInput(anyInt(), anyInt(), anyBoolean(), anyBoolean());
        verify(provider, never()).handleInput(anyInt(), anyInt(), anyBoolean(), anyBoolean());
        invoke(loop, "isSpecialStageRewindable", new Class<?>[0]); verify(other, never()).supportsRewind(); verify(provider).supportsRewind();
    }
    @Test void passCountRemainsRepeatedAndDebugRebindingKeepsCapturedFrameProvider() throws Exception {
        stage(); when(provider.debugCapabilities()).thenReturn(SpecialStageDebugCapabilities.LEGACY);
        engine.configuration().setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
        when(input.isKeyPressedWithoutModifiers(org.lwjgl.glfw.GLFW.GLFW_KEY_X)).thenReturn(true);
        var later = mock(SpecialStageProvider.class); var laterCamera = mock(Camera.class); var laterInput = mock(InputHandler.class);
        var laterContext = mock(GameplayModeContext.class); var laterFade = mock(FadeManager.class);
        var capturedRewind = mock(com.openggf.game.rewind.LiveRewindManager.class);
        var laterRewind = mock(com.openggf.game.rewind.LiveRewindManager.class);
        write(loop, "liveRewindManager", capturedRewind);
        when(later.supportsRewind()).thenReturn(true);
        when(laterInput.logical()).thenReturn(LogicalInputSnapshot.neutral());
        when(laterContext.getWorldSession()).thenReturn(context.getWorldSession());
        doAnswer(c -> {
            retained(later); write(loop, "camera", laterCamera); write(loop, "inputHandler", laterInput);
            write(loop, "gameplayMode", laterContext); write(loop, "fadeManager", laterFade);
            write(loop, "liveRewindManager", laterRewind);
            return null;
        }).when(provider).debugNextStage();
        invoke(loop, "doEnterSpecialStage", new Class<?>[]{SpecialStageProvider.class,int.class,boolean.class}, provider, 0, false);
        when(provider.isEntryPresentationReady()).thenReturn(true);
        GameLoopTestStep.invoke(loop, "updateSpecialStageMode", new Class<?>[0]);
        verify(provider).update(); verify(later, never()).update(); verify(later).handleInput(anyInt(), anyInt(), anyBoolean(), anyBoolean());
        verify(laterCamera).setX((short)0); verify(camera, never()).setX(anyShort());
        verify(provider).getStageMusic(); verify(later, never()).getStageMusic();
        verify(laterContext).getWorldSession(); verify(laterContext, never()).plcFrameLifecycle();
        verify(fade).startFadeFromWhite(any()); verify(laterFade, never()).startFadeFromWhite(any());
        verify(capturedRewind).recordExternalFrame(context, GameMode.SPECIAL_STAGE, provider, false, input, false);
        verify(laterRewind, never()).recordExternalFrame(any(), any(), any(), anyBoolean(), any(), anyBoolean());
        write(loop, "gameplayMode", context); write(loop, "fadeManager", fade);
        var pacing = mock(GameLoop.SpecialStageObservationPacing.class);
        when(pacing.passCount()).thenReturn(2, 2, 2);
        loop.setSpecialStageObservationPacing(pacing); when(input.isKeyPressedWithoutModifiers(anyInt())).thenReturn(false);
        GameLoopTestStep.invoke(loop, "updateSpecialStageMode", new Class<?>[0]); verify(pacing, times(3)).passCount();
        verify(pacing).runPass(0, later); verify(pacing).afterPass(1);
    }
    @Test void debugCallbacksCaptureTheirOriginalReceiverBeforeFirstDebugMutation() throws Exception {
        stage(); engine.configuration().setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
        var first = mock(GameLoopDebugShortcuts.class); var later = mock(GameLoopDebugShortcuts.class);
        write(loop, "debugShortcuts", first);
        when(provider.debugCapabilities()).thenReturn(SpecialStageDebugCapabilities.LEGACY);
        when(input.isKeyPressedWithoutModifiers(org.lwjgl.glfw.GLFW.GLFW_KEY_X)).thenReturn(true);
        when(input.isKeyPressedWithoutModifiers(engine.configuration().getInt(SonicConfiguration.SPECIAL_STAGE_COMPLETE_KEY)))
                .thenReturn(true);
        doAnswer(c -> { write(loop, "debugShortcuts", later); return null; }).when(provider).debugNextStage();
        GameLoopTestStep.invoke(loop, "updateSpecialStageMode", new Class<?>[0]);
        verify(first).debugCompleteSpecialStageWithEmerald();
        verify(later, never()).debugCompleteSpecialStageWithEmerald();
    }

    @Test void delayedResultsUsesCompletionTimeProviderButEarlierOutcome() throws Exception {
        stage(); var next = mock(SpecialStageProvider.class); var completion = new AtomicReference<Runnable>();
        when(provider.getRingsCollected()).thenReturn(65); when(provider.getCurrentStage()).thenReturn(6);
        doAnswer(c -> { completion.set(c.getArgument(0)); return null; }).when(fade).startFadeToWhite(any());
        enterResults(false); retained(next); completion.get().run();
        verify(next).resetForResults(); verify(next).createResultsScreen(65, false, 6, 0);
        verify(provider, never()).resetForResults();
    }
    @Test void superRequestUsesRewardAwareInitializerAndOrdinaryKeepsVirtualEntrySeam() throws Exception {
        invoke(loop, "enterSpecialStage", new Class<?>[]{SpecialStageEntryRequest.class},
                new SpecialStageEntryRequest(3, EmeraldRewardKind.SUPER_EMERALD));
        verify(provider).initializeStage(3, SpecialStageStartupPolicy.FAST, EmeraldRewardKind.SUPER_EMERALD);
        verify(provider, never()).initializeStage(anyInt(), any(SpecialStageStartupPolicy.class));
        int[] calls = {0};
        loop = new GameLoop(engine, input) {
            @Override void doEnterSpecialStage(SpecialStageProvider selected, int index, boolean black) {
                assertSame(provider, selected); calls[0]++;
            }
        };
        write(loop, "spriteManager", sprites); write(loop, "fadeManager", fade);
        loop.enterSpecialStage(); assertEquals(1, calls[0]);
    }
    @Test void entryRegistersResolvedContextButPresentationReadsThenCurrentBoundContext() throws Exception {
        var bound = mock(GameplayModeContext.class);

        when(bound.plcFrameLifecycle()).thenReturn(context.plcFrameLifecycle());
        doAnswer(c -> { write(loop, "gameplayMode", bound); return null; })
                .when(provider).initializeStage(0, SpecialStageStartupPolicy.FAST);
        loop.doEnterSpecialStage(provider, 0, false, SpecialStageStartupPolicy.FAST);
        verify(bound).registerSpecialStageAdapter(provider); verify(bound).plcFrameLifecycle();
    }
    @Test void fallbackReturnRetiresResultsBeforeLastPostAndKeepsIoWrapper() throws Exception {
        mode(GameMode.SPECIAL_STAGE_RESULTS); retained(provider); write(loop, "resultsScreen", mock(ResultsScreen.class));
        doAnswer(c -> { assertNull(loop.getResultsScreen()); assertSame(NoOpSpecialStageProvider.INSTANCE, loop.getActiveSpecialStageProvider()); return null; })
                .when(levels).setLastStarPostHit();
        var failure = new IOException("fallback"); doThrow(failure).when(levels).loadZoneAndAct(0, 0);
        var caught = assertThrows(RuntimeException.class, () -> invoke(loop, "doExitResultsScreen", new Class<?>[0]));
        assertEquals("Failed to load starting level after special stage", caught.getMessage()); assertSame(failure, caught.getCause());
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
    }
    @Test void modeChangesClearPresentationButRetainResultsAndTransitionLatches() {
        write(loop, "resultsScreen", mock(ResultsScreen.class)); write(loop, "resultsExitReady", true);
        write(loop, "returningFromSpecialStage", true); retained(provider);
        mode(GameMode.SPECIAL_STAGE); loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        assertSame(provider, read(loop, "activeSpecialStageProvider")); assertNotNull(loop.getResultsScreen());
        assertTrue((boolean)read(loop, "resultsExitReady")); assertTrue((boolean)read(loop, "returningFromSpecialStage"));
    }

    @Test void titleReleaseSpecialBranchSkipsOrdinaryZoneCallbackAndBonusWinsFirst() throws Exception {
        var events = mock(LevelEventProvider.class); when(module.getLevelEventProvider()).thenReturn(events);
        mode(GameMode.TITLE_CARD); write(loop, "returningFromSpecialStage", true);
        invoke(loop, "exitTitleCard", new Class<?>[0]); assertFalse((boolean)read(loop, "returningFromSpecialStage"));
        verify(events, never()).applyZonePlayerStateAfterTitleCard();
        mode(GameMode.TITLE_CARD); write(loop, "returningFromSpecialStage", true);
        write(loop, "postTitleCardDestination", PostTitleCardDestination.BONUS_STAGE);
        invoke(loop, "exitTitleCard", new Class<?>[0]); assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode());
        assertTrue((boolean)read(loop, "returningFromSpecialStage"));
        mode(GameMode.TITLE_CARD); write(loop, "returningFromSpecialStage", false);
        invoke(loop, "exitTitleCard", new Class<?>[0]); verify(events).applyZonePlayerStateAfterTitleCard();
    }
}
