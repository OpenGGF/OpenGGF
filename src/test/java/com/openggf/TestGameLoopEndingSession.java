package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.resources.*;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.*;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Temporal characterization first run against the pre-extraction GameLoop. */
@Isolated
class TestGameLoopEndingSession {
    private ProbeLoop loop;
    private GameModule module;
    private EndingProvider provider;
    private AudioManager audio;
    private LevelManager levels;
    private SpriteManager sprites;
    private Camera camera;
    private FadeManager fade;
    private InputHandler input;
    private AbstractPlayableSprite player;

    @BeforeEach void setUp() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.resetPerTest();
        module = mock(GameModule.class);
        when(module.getRules()).thenReturn(GameRules.SONIC_2);
        when(module.createRuntimeArtCoordinator(any())).thenReturn(RuntimeArtCoordinator.NONE);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);
        SessionManager.clear();
        SessionManager.openGameplaySession(module);
        TestEnvironment.activeGameplayMode();
        audio = mock(AudioManager.class);
        var old = EngineServices.current();
        var injected = new EngineContext(old.configuration(), old.graphics(), audio, old.roms(), old.profiler(),
                old.debugOverlay(), old.playbackDebug(), old.romDetection(), old.crossGameFeatures(),
                old.moduleResolutionService());
        input = mock(InputHandler.class);
        when(input.logical()).thenReturn(com.openggf.control.LogicalInputSnapshot.neutral());
        loop = new ProbeLoop(injected, input);
        levels = mock(LevelManager.class);
        sprites = mock(SpriteManager.class);
        camera = mock(Camera.class);
        fade = mock(FadeManager.class);
        player = mock(AbstractPlayableSprite.class);
        when(sprites.getSprite(anyString())).thenReturn(player);
        field(loop, "levelManager", levels);
        field(loop, "spriteManager", sprites);
        field(loop, "camera", camera);
        field(loop, "fadeManager", fade);
        provider = mock(EndingProvider.class);
        when(provider.getCurrentPhase()).thenReturn(EndingPhase.CUTSCENE);
        when(module.getEndingProvider()).thenReturn(provider);
        clearInvocations(audio, module);
    }

    @AfterEach void tearDown() { SessionManager.clear(); }

    @Test void factoryIsLookedUpAgainAfterFadeAndModuleRebinding() throws Exception {
        var first = mock(EndingProvider.class);
        when(module.getEndingProvider()).thenReturn(first);
        AtomicReference<Runnable> completion = whiteCompletion();
        endingCall("startEndingFade");
        assertTrue(pending());
        verify(module, times(1)).getEndingProvider();
        verify(first).saveReasonOnEndingStart();
        verify(first, never()).initialize();
        var secondModule = mock(GameModule.class);
        when(secondModule.getRules()).thenReturn(GameRules.SONIC_2);
        when(secondModule.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);
        when(secondModule.createRuntimeArtCoordinator(any())).thenReturn(RuntimeArtCoordinator.NONE);
        when(secondModule.getEndingProvider()).thenReturn(provider);
        SessionManager.clear();
        SessionManager.openGameplaySession(secondModule);
        loop.setGameplayMode(TestEnvironment.activeGameplayMode());
        completion.get().run();
        assertSame(provider, loop.getEndingProvider());
        assertFalse(pending());
        verify(secondModule, times(1)).getEndingProvider();
        verify(provider).initialize();
        var order = inOrder(audio, fade);
        order.verify(audio).fadeOutMusic();
        order.verify(fade).startFadeToWhite(any());
        order.verify(fade).deferFirstStepToNextVint();
    }

    @Test void sameFactoryProducesTwoDistinctProviders() throws Exception {
        var first = mock(EndingProvider.class);
        when(module.getEndingProvider()).thenReturn(first, provider);
        var completion = whiteCompletion();
        endingCall("startEndingFade");
        completion.get().run();
        verify(module, times(2)).getEndingProvider();
        verify(first, never()).initialize();
        verify(provider).initialize();
        assertSame(provider, loop.getEndingProvider());
    }

    @Test void failedInitializerKeepsAssignedProviderAndPendingWithoutModeOrReveal() throws Exception {
        var completion = whiteCompletion();
        endingCall("startEndingFade");
        var failure = new IllegalStateException("initializer");
        doThrow(failure).when(provider).initialize();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> completion.get().run()));
        assertSame(provider, loop.getEndingProvider());
        assertTrue(pending());
        verify(fade, never()).startFadeFromWhite(any());
        assertTrue(loop.modes.isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void providerRevealSurvivesUnlessItIsHoldingWhite(boolean holdWhite) throws Exception {
        when(fade.isActive()).thenReturn(true);
        when(fade.getState()).thenReturn(holdWhite ? FadeManager.FadeState.HOLD_WHITE : FadeManager.FadeState.FADING_FROM_BLACK);
        endingCall("doEnterEnding");
        verify(fade, times(holdWhite ? 1 : 0)).startFadeFromWhite(any());
        assertEquals(List.of(GameMode.ENDING_CUTSCENE), loop.modes);
    }

    @Test void nativeLifecycleBindsBeforeInitializeAndRevealUsesPostModeFadeSnapshot() throws Exception {
        provider = mock(EndingProvider.class, withSettings().extraInterfaces(NativeFadeLifecycleAware.class));
        when(provider.getCurrentPhase()).thenReturn(EndingPhase.CUTSCENE);
        when(module.getEndingProvider()).thenReturn(provider);
        var afterInitialize = mock(FadeManager.class);
        var afterMode = mock(FadeManager.class);
        doAnswer(call -> { field(loop, "fadeManager", afterInitialize); return null; }).when(provider).initialize();
        loop.afterMode = () -> uncheckedField(loop, "fadeManager", afterMode);
        endingCall("doEnterEnding");
        var ordered = inOrder(provider);
        ordered.verify((NativeFadeLifecycleAware) provider).bindNativeFadeLifecycle(
                SessionManager.getCurrentGameplayMode().plcFrameLifecycle());
        ordered.verify(provider).initialize();
        verify(afterMode).startFadeFromWhite(any());
        verifyNoInteractions(afterInitialize, fade);
    }

    @Test void nullProviderClearsPendingAndInitializesLazyTitleAfterVirtualModeWrite() throws Exception {
        var completion = whiteCompletion();
        endingCall("startEndingFade");
        var title = mock(TitleScreenProvider.class);
        when(module.getEndingProvider()).thenReturn(null);
        when(module.getTitleScreenProvider()).thenReturn(title);
        doAnswer(call -> { assertFalse(pending()); assertEquals(GameMode.TITLE_SCREEN, loop.getCurrentGameMode()); return null; })
                .when(title).initialize();
        completion.get().run();
        assertNull(loop.getEndingProvider());
        verify(title).initialize();
        verify(fade, never()).startFadeFromWhite(any());
    }

    @ParameterizedTest @CsvSource({"true,false,1", "false,true,2", "false,false,2"})
    void frozenSamplingShortCircuitsAndConsumesGameplayQueries(boolean before, boolean after, int queries) throws Exception {
        installProvider();
        when(provider.shouldAdvanceFrozenDemoScene()).thenReturn(before, after);
        when(provider.shouldRunDemoGameplay()).thenReturn(false);
        endingCall("updateEndingCreditsDemo");
        verify(provider, times(queries)).shouldAdvanceFrozenDemoScene();
        verify(provider, times(2)).shouldRunDemoGameplay();
        verify(player).setForcedInputMask(0);
        var ordered = inOrder(provider, sprites, player, levels);
        ordered.verify(provider).shouldAdvanceFrozenDemoScene();
        ordered.verify(provider).update();
        ordered.verify(sprites).getSprite(anyString());
        ordered.verify(player).setForcedInputMask(0);
        if (before || after) {
            ordered.verify(levels).updateObjectPositionsWithoutTouches();
            ordered.verify(levels).updateEndingDemoScene();
        } else verify(levels, never()).updateEndingDemoScene();
        verify(sprites, never()).update(any());
        verifyNoInteractions(camera);
    }

    @ParameterizedTest @CsvSource({"true,false", "false,false", "true,true", "false,true"})
    void demoPhysicsPreservesWaterObjectsCameraEventsAndCounterOrder(boolean afterPlayer, boolean frozen) throws Exception {
        installProvider();
        var events = mock(LevelEventProvider.class);
        when(module.getLevelEventProvider()).thenReturn(events);
        when(provider.shouldRunDemoGameplay()).thenReturn(true);
        when(provider.getDemoInputMask()).thenReturn(8);
        when(provider.isScrollFrozen()).thenReturn(frozen);
        when(levels.advanceWaterLevelBeforePlayerPhysics()).thenReturn(true);
        when(levels.objectsExecuteAfterPlayerPhysics()).thenReturn(afterPlayer);
        endingCall("updateEndingCreditsDemo");
        var order = inOrder(provider, player, sprites, levels, camera, events);
        order.verify(player).setForcedInputMask(8);
        order.verify(sprites).publishHeldInputForLevelEvents(input);
        order.verify(levels).updateZoneFeaturesPrePhysics();
        order.verify(levels).advanceWaterLevelBeforePlayerPhysics();
        order.verify(levels).advanceDynamicWaterLevel();
        order.verify(levels).objectsExecuteAfterPlayerPhysics();
        if (afterPlayer) {
            order.verify(sprites).update(input);
            order.verify(levels).updateObjectPositionsPostPhysicsWithoutTouches();
        } else {
            order.verify(levels).updateObjectPositions();
            order.verify(sprites).update(input);
        }
        order.verify(provider).isScrollFrozen();
        if (!frozen) {
            order.verify(camera).updatePosition();
            order.verify(camera).captureRenderCopy();
        }
        order.verify(events).update();
        if (!frozen) {
            order.verify(camera).updateBoundaryEasing();
            order.verify(events).updateAfterCameraBoundaryEasing();
            order.verify(levels).postCameraObjectPlacementSync();
            order.verify(levels).advanceLevelFrameCounter();
            order.verify(levels).update();
            order.verify(levels).refreshObjectPostCameraRenderState();
        } else {
            verifyNoInteractions(camera);
            verify(levels, never()).advanceLevelFrameCounter();
        }
        verify(provider, times(2)).shouldRunDemoGameplay();
        verify(player, never()).setControlLocked(anyBoolean());
    }

    @Test void providerCallbackRebindsLiveInputSpritesAndLevelBeforeDemoWork() throws Exception {
        installProvider();
        var rebound = mock(SpriteManager.class);
        var reboundLevels = mock(LevelManager.class);
        var reboundInput = mock(InputHandler.class);
        when(provider.shouldRunDemoGameplay()).thenReturn(true);
        doAnswer(call -> {
            field(loop, "spriteManager", rebound); field(loop, "levelManager", reboundLevels);
            loop.setInputHandler(reboundInput); return null;
        }).when(provider).update();
        endingCall("updateEndingCreditsDemo");
        verify(rebound).getSprite(anyString());
        verify(rebound).update(reboundInput);
        verify(reboundLevels).updateZoneFeaturesPrePhysics();
        verifyNoInteractions(levels, sprites);
    }

    @Test void demoRequestIsConsumedBeforeLoadAndPostLoadManagersAreReadLive() throws Exception {
        installProvider();
        when(provider.hasDemoLoadRequest()).thenReturn(true);
        when(provider.getDemoZone()).thenReturn(3);
        when(provider.getDemoAct()).thenReturn(1);
        var reboundLevels = mock(LevelManager.class);
        var reboundSprites = mock(SpriteManager.class);
        doAnswer(call -> {
            field(loop, "levelManager", reboundLevels); field(loop, "spriteManager", reboundSprites); return null;
        }).when(levels).loadZoneAndAct(3, 1);
        endingCall("updateEndingCreditsText");
        var order = inOrder(provider, levels, reboundLevels, reboundSprites);
        order.verify(provider).update();
        order.verify(provider).hasDemoLoadRequest();
        order.verify(provider).consumeDemoLoadRequest();
        order.verify(levels).setSuppressNextMusicChange(true);
        order.verify(levels).loadZoneAndAct(3, 1);
        order.verify(reboundLevels).consumeTitleCardRequest();
        order.verify(reboundLevels).setForceHudSuppressed(true);
        order.verify(reboundLevels).prepareEndingDemoScene();
        order.verify(reboundSprites).setInputSuppressed(true);
        order.verify(provider).onDemoZoneLoaded();
        assertEquals(GameMode.CREDITS_DEMO, loop.getCurrentGameMode());
        verify(levels, never()).consumeTitleCardRequest();
    }

    @Test void lamppostRestoresPlayerCameraAndOneLocalWaterSnapshot() throws Exception {
        installProvider();
        when(provider.getDemoLamppostState()).thenReturn(new DemoLamppostState(10, 20, 13, 30, 40, 50, 60, 7));
        var water = mock(WaterSystem.class);
        field(loop, "waterSystem", water);
        when(levels.getFeatureZoneId()).thenReturn(4);
        when(levels.getFeatureActId()).thenReturn(2);
        var features = mock(ZoneFeatureProvider.class);
        when(levels.getZoneFeatureProvider()).thenReturn(features);
        endingCall("loadEndingDemoZone");
        var order = inOrder(player, camera, levels, water, features, sprites);
        order.verify(player).setRingCount(0);
        order.verify(player).setCentreX((short) 10);
        order.verify(player).setCentreY((short) 20);
        order.verify(player).setRingCount(13);
        order.verify(camera).setX((short) 30);
        order.verify(camera).setY((short) 40);
        order.verify(camera).setMaxY((short) 50);
        order.verify(water).setWaterLevelDirect(4, 2, 60);
        order.verify(water).setWaterLevelTarget(4, 2, 60);
        order.verify(features).setWaterRoutine(7);
        order.verify(levels).prepareEndingDemoScene();
        order.verify(sprites).setInputSuppressed(true);
        verify(camera, never()).updatePosition(true);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exitCapturesDependenciesBeforeCleanupAndClearsProviderBeforeSharedHandoff(boolean activeFade) throws Exception {
        installProvider();
        when(fade.isActive()).thenReturn(activeFade);
        var reboundLevels = mock(LevelManager.class);
        var reboundSprites = mock(SpriteManager.class);
        var reboundFade = mock(FadeManager.class);
        doAnswer(call -> {
            field(loop, "levelManager", reboundLevels); field(loop, "spriteManager", reboundSprites);
            field(loop, "fadeManager", reboundFade); return null;
        }).when(sprites).setInputSuppressed(false);
        var completion = new AtomicReference<Runnable>();
        doAnswer(call -> { completion.set(call.getArgument(0)); return null; }).when(fade).startFadeToBlack(any());
        loop.afterMode = () -> assertNull(loop.getEndingProvider());
        endingCall("exitEndingToTitleScreen");
        var order = inOrder(sprites, levels, player, audio, fade);
        order.verify(sprites).setInputSuppressed(false);
        order.verify(levels).setForceHudSuppressed(false);
        order.verify(sprites).getSprite(anyString());
        order.verify(player).setControlLocked(false);
        order.verify(player).clearForcedInputMask();
        order.verify(audio).fadeOutMusic();
        order.verify(fade).isActive();
        if (!activeFade) {
            assertSame(provider, loop.getEndingProvider());
            assertNotNull(completion.get());
            completion.get().run();
        }
        assertNull(loop.getEndingProvider());
        assertEquals(GameMode.TITLE_SCREEN, loop.getCurrentGameMode());
        verify(reboundSprites).setInputSuppressed(false);
        verify(reboundLevels).setLevelInactiveForTransition(false);
        verify(reboundFade).startFadeFromBlack(any());
    }

    @Test void requestsAreAcknowledgedBeforeReturnCleanupAndPostCreditsExitShortCircuits() throws Exception {
        installProvider();
        when(provider.hasTextReturnRequest()).thenReturn(true);
        endingCall("updateEndingCreditsDemo");
        var order = inOrder(provider, sprites, levels, player);
        order.verify(provider).hasTextReturnRequest();
        order.verify(provider).consumeTextReturnRequest();
        order.verify(sprites).setInputSuppressed(false);
        order.verify(levels).setForceHudSuppressed(false);
        order.verify(player).clearForcedInputMask();
        order.verify(provider).onReturnToText();
        clearInvocations(provider);
        when(provider.consumePostCreditsExitRequest()).thenReturn(true);
        endingCall("updateEndingPostCredits");
        verify(provider).updatePostCredits(input);
        verify(provider, never()).isComplete();
    }

    @Test void refreshProviderResetGenericModesAndParkResumeRetainEndingState() throws Exception {
        installProvider();
        var completion = whiteCompletion();
        endingCall("startEndingFade");
        loop.resetModuleScopedProviders();
        loop.setGameMode(GameMode.CREDITS_DEMO);
        loop.refreshRuntimeBindings();
        assertSame(provider, loop.getEndingProvider());
        assertTrue(pending());
        var parked = loop.suspension.park();
        assertEquals(GameMode.CREDITS_DEMO, parked);
        assertNull(loop.resolveGameplayModeContext());
        assertSame(provider, loop.getEndingProvider());
        loop.suspension.unpark(parked);
        assertEquals(GameMode.CREDITS_DEMO, loop.getCurrentGameMode());
        assertSame(provider, loop.getEndingProvider());
        assertTrue(pending());
        completion.get().run();
        assertFalse(pending());
    }

    @Test void standaloneCompletionAliasesTheActualFreezeAndRewindGatesWithoutPlcWrapping() throws Exception {
        field(loop, "currentGameMode", GameMode.LEVEL);
        var completion = new AtomicReference<Runnable>();
        doAnswer(invocation -> {
            assertTrue((boolean) call(loop, "isNonRewindableTransitionPending"));
            assertTrue((boolean) call(loop, "isRewindBlocked"));
            completion.set(invocation.getArgument(0)); return null;
        }).when(fade).startFadeToBlack(any());
        var launch = (com.openggf.game.launch.MasterTitleLaunchCoordinator) read(loop, "masterTitleLaunchCoordinator");
        launch.setReturnToMasterTitleHandler(() -> {
            try { assertFalse(pending()); } catch (Exception e) { throw new AssertionError(e); }
        });
        var coordinator = (com.openggf.game.launch.MasterTitleExitCoordinator) read(loop, "masterTitleExitCoordinator");
        coordinator.startStandaloneCompletion();
        assertTrue(pending());
        loop.step();
        verify(sprites, never()).update(any());
        verify(levels, never()).updateZoneFeaturesPrePhysics();
        var order = inOrder(audio, fade);
        order.verify(audio).fadeOutMusic();
        order.verify(fade).startFadeToBlack(any());
        completion.get().run();
        assertFalse((boolean) call(loop, "isNonRewindableTransitionPending"));
        assertFalse((boolean) call(loop, "isRewindBlocked"));
        assertNull(loop.getEndingProvider());
    }

    @ParameterizedTest @EnumSource(EndingPhase.class)
    void actualModeDispatchClaimsAndPreparesTheProviderPhase(EndingPhase phase) throws Exception {
        installProvider();
        when(provider.getCurrentPhase()).thenReturn(phase);
        var plc = mock(PlcLifecycleService.class);
        when(plc.hasPreparationBoundary(any())).thenReturn(true);
        when(module.getGameService(PlcLifecycleService.class)).thenReturn(plc);
        field(loop, "currentGameMode", GameMode.ENDING_CUTSCENE);
        loop.step();
        var expected = switch (phase) {
            case CUTSCENE -> PlcLifecyclePhase.ENDING;
            case CREDITS_TEXT -> PlcLifecyclePhase.CREDITS_TEXT;
            case CREDITS_DEMO -> PlcLifecyclePhase.CREDITS_DEMO;
            case POST_CREDITS, FINISHED -> PlcLifecyclePhase.POST_CREDITS;
        };
        verify(plc).serviceVBlank(expected);
        verify(plc).prepareAfterLoop(expected);
        verify(input).update();
    }

    @Test void nullProviderStillClaimsAndPreparesEndingPlc() throws Exception {
        var plc = mock(PlcLifecycleService.class);
        when(plc.hasPreparationBoundary(any())).thenReturn(true);
        when(module.getGameService(PlcLifecycleService.class)).thenReturn(plc);
        field(loop, "currentGameMode", GameMode.ENDING_CUTSCENE);
        loop.step();
        verify(plc).serviceVBlank(PlcLifecyclePhase.ENDING);
        verify(plc).prepareAfterLoop(PlcLifecyclePhase.ENDING);
        verify(input).update();
        verifyNoInteractions(provider);
    }

    @Test void plcOverrideWinsOverEndingPhaseWithoutAnExtraPhaseQuery() throws Exception {
        installProvider();
        when(provider.plcLifecyclePhaseOverride()).thenReturn(Optional.of(PlcLifecyclePhase.CREDITS_TEXT));
        var plc = mock(PlcLifecycleService.class);
        when(plc.hasPreparationBoundary(any())).thenReturn(true);
        when(module.getGameService(PlcLifecycleService.class)).thenReturn(plc);
        field(loop, "currentGameMode", GameMode.ENDING_CUTSCENE);
        loop.step();
        verify(plc).serviceVBlank(PlcLifecyclePhase.CREDITS_TEXT);
        verify(plc).prepareAfterLoop(PlcLifecyclePhase.CREDITS_TEXT);
        verify(provider, times(2)).getCurrentPhase(); // dispatch and mode synchronization only
    }

    @Test void nativeBindingUsesBoundContextEvenWhenResolvedContextExists() throws Exception {
        provider = mock(EndingProvider.class, withSettings().extraInterfaces(NativeFadeLifecycleAware.class));
        when(module.getEndingProvider()).thenReturn(provider);
        field(loop, "gameplayMode", null);
        assertNotNull(loop.resolveGameplayModeContext());
        assertThrows(NullPointerException.class, () -> endingCall("doEnterEnding"));
        assertSame(provider, loop.getEndingProvider());
        verify(provider, never()).initialize();
    }

    @Test void constructorStoresCapabilitiesWithoutQueryingTheRuntime() {
        var host = mock(GameLoopEndingSession.Host.class);
        var stableAudio = mock(AudioManager.class);
        var owner = new GameLoopEndingSession(host, stableAudio);
        assertNull(owner.provider());
        assertFalse(owner.isTransitionPending());
        verifyNoInteractions(host, stableAudio);
    }

    @Test void actualProviderRevealCallbackSurvivesEntry() throws Exception {
        var reveal = new FadeManager();
        field(loop, "fadeManager", reveal);
        var completed = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> { reveal.startFadeFromBlack(completed::incrementAndGet); return null; })
                .when(provider).initialize();
        endingCall("doEnterEnding");
        assertEquals(FadeManager.FadeState.FADING_FROM_BLACK, reveal.getState());
        assertTrue(reveal.hasPendingCompletion());
        for (int n = 0; n < 30; n++) reveal.update();
        assertEquals(1, completed.get(), "host reveal must not replace the provider's callback");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void gameplayRequestIsResampledRatherThanCached(boolean first) throws Exception {
        installProvider();
        when(provider.shouldRunDemoGameplay()).thenReturn(first, !first);
        when(provider.getDemoInputMask()).thenReturn(8);
        endingCall("updateEndingCreditsDemo");
        verify(player).setForcedInputMask(first ? 8 : 0);
        verify(sprites, times(first ? 0 : 1)).update(input);
        verify(provider, times(2)).shouldRunDemoGameplay();
        verify(provider, times(first ? 1 : 0)).getDemoInputMask();
    }

    @Test void loadModeCallbackRebindsProviderBeforeLoadedNotification() throws Exception {
        installProvider();
        var afterMode = mock(EndingProvider.class);
        loop.afterMode = () -> {
            try { field(endingTarget(), "endingProvider", afterMode); }
            catch (Exception e) { throw new AssertionError(e); }
        };
        endingCall("loadEndingDemoZone");
        verify(afterMode).onDemoZoneLoaded();
        verify(provider, never()).onDemoZoneLoaded();
        assertSame(afterMode, loop.getEndingProvider());
    }

    @Test void entryKeepsItsPostModeFadeSnapshotWhileResolvingTheWrappingContext() throws Exception {
        var host = mock(GameLoopEndingSession.Host.class);
        when(host.module()).thenReturn(module);
        var first = mock(FadeManager.class);
        var later = mock(FadeManager.class);
        var slot = new AtomicReference<>(first);
        when(host.fade()).thenAnswer(call -> slot.get());
        when(host.resolvedGameplayContext()).thenAnswer(call -> { slot.set(later); return null; });
        var owner = new GameLoopEndingSession(host, audio);
        call(owner, "doEnterEnding");
        verify(host, times(1)).fade();
        verify(host, times(1)).resolvedGameplayContext();
        verify(first).startFadeFromWhite(any());
        verifyNoInteractions(later);
    }

    private AtomicReference<Runnable> whiteCompletion() {
        var completion = new AtomicReference<Runnable>();
        doAnswer(call -> { completion.set(call.getArgument(0)); return null; }).when(fade).startFadeToWhite(any());
        return completion;
    }
    private Object endingTarget() throws Exception { return read(loop, "endingSession"); }
    private void installProvider() throws Exception { field(endingTarget(), "endingProvider", provider); }
    private boolean pending() throws Exception { return (boolean) read(endingTarget(), "endingTransitionPending"); }
    private void endingCall(String name) throws Exception { call(endingTarget(), name); }
    private static Object read(Object target, String name) throws Exception {
        var f = (target instanceof GameLoop ? GameLoop.class : target.getClass()).getDeclaredField(name);
        f.setAccessible(true); return f.get(target);
    }
    private static void field(Object target, String name, Object value) throws Exception {
        var f = (target instanceof GameLoop ? GameLoop.class : target.getClass()).getDeclaredField(name);
        f.setAccessible(true); f.set(target, value);
    }
    private static void uncheckedField(Object target, String name, Object value) {
        try { field(target, name, value); } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static Object call(Object target, String name) throws Exception {
        var m = (target instanceof GameLoop ? GameLoop.class : target.getClass()).getDeclaredMethod(name);
        m.setAccessible(true);
        try { return m.invoke(target); } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }
    private static class ProbeLoop extends GameLoop {
        final List<GameMode> modes = new ArrayList<>();
        Runnable afterMode;
        ProbeLoop(EngineContext engine, InputHandler input) { super(engine, input); }
        @Override public void setGameMode(GameMode mode) {
            super.setGameMode(mode); modes.add(mode);
            if (afterMode != null) afterMode.run();
        }
    }
}
