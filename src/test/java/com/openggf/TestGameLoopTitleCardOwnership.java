package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.*;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.*;
import com.openggf.game.titlecard.TitleCardLoopTail;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.TitleCardLifecycleTestAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Preservation controls first exercised against unchanged b0865737 production. */
@Isolated
class TestGameLoopTitleCardOwnership {
    private Probe loop;
    private EngineContext engine;
    private GameModule module;
    private GameplayModeContext context;
    private LevelManager level;
    private SpriteManager sprites;
    private Camera camera;
    private InputHandler input;
    private TitleCardProvider title;
    private LevelEventProvider events;

    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.resetPerTest(); SessionManager.clear();
        module = mock(GameModule.class);
        when(module.getRules()).thenReturn(GameRules.SONIC_2);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);
        when(module.createRuntimeArtCoordinator(any())).thenReturn(RuntimeArtCoordinator.NONE);
        SessionManager.openGameplaySession(module); context = TestEnvironment.activeGameplayMode();
        var old = EngineServices.current();
        engine = new EngineContext(old.configuration(), old.graphics(), mock(AudioManager.class), old.roms(),
                old.profiler(), old.debugOverlay(), old.playbackDebug(), old.romDetection(), old.crossGameFeatures(), old.moduleResolutionService());
        input = mock(InputHandler.class); when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        loop = new Probe(); level = mock(LevelManager.class); sprites = mock(SpriteManager.class);
        camera = mock(Camera.class); title = mock(TitleCardProvider.class); events = mock(LevelEventProvider.class);
        when(module.getTitleCardProvider()).thenReturn(title); when(module.getLevelEventProvider()).thenReturn(events);
        when(sprites.getAllSprites()).thenReturn(List.of());
        bind(level, sprites, camera); provider(loop, title);
        raw("activePlcLifecycleFrame", mock(PlcLifecycleFrame.class));
        clearInvocations(module);
    }
    @AfterEach void cleanup() { SessionManager.clear(); }
    private void raw(String name, Object value) { SpecialStageLifecycleTestAccess.writeField(loop, name, value); }
    private Object raw(String name) { return SpecialStageLifecycleTestAccess.readField(loop, name); }
    private void mode(GameMode value) { raw("currentGameMode", value); }
    private void bind(LevelManager l, SpriteManager s, Camera c) { raw("levelManager", l); raw("spriteManager", s); raw("camera", c); }
    private void titleMode() { mode(GameMode.TITLE_CARD); }
    private void returning(boolean value) { SpecialStageLifecycleTestAccess.write(loop, "returningFromSpecialStage", value); }
    private boolean returning() { return (boolean) SpecialStageLifecycleTestAccess.read(loop, "returningFromSpecialStage"); }
    private void deferred(Runnable r) {
        var p = mock(BonusStageProvider.class);
        doAnswer(c -> { r.run(); return null; }).when(p).onDeferredSetupComplete();
        BonusStageSessionTestAccess.deferred(loop, p);
    }

    private class Probe extends GameLoop {
        Runnable unlock = () -> {}, entering = () -> {}, shield = () -> {};
        AbstractPlayableSprite playable;
        int locks, characters, players, publicGets, bonusBoundaries;
        Probe() { super(engine, input); }
        @Override GameMode changeGameModeForBoundary(GameMode next) {
            GameMode old = getCurrentGameMode(); raw("currentGameMode", next); entering.run(); return old;
        }
        @Override GameMode changeGameModeWithoutRewindBoundary(GameMode next) {
            GameMode old = getCurrentGameMode(); raw("currentGameMode", next); return old;
        }
        @Override void markBonusEntryNonRewindableBoundary() { bonusBoundaries++; }
        @Override void applyTitleCardControlLock(boolean locked) {
            locks++; super.applyTitleCardControlLock(locked); if (!locked) unlock.run();
        }
        @Override String resolveMainCharacterCode() { characters++; return "sonic"; }
        @Override AbstractPlayableSprite resolveMainPlayableSprite() { players++; return playable; }
        @Override void applyPendingBonusStageShieldRestore(AbstractPlayableSprite p) { assertSame(playable, p); shield.run(); }
        @Override public TitleCardProvider getTitleCardProvider() { publicGets++; return super.getTitleCardProvider(); }
    }

    @Test void constructionSamplesNoTitleProviderButAllowsExistingRootModuleReads() {
        new GameLoop(engine, input);
        verify(module, never()).getTitleCardProvider();
        // Root's existing runtime-binding/publication construction may read module rules.
        assertEquals(0, loop.publicGets);
    }
    @Test void ownerConstructionStoresOnlyTheHostWithoutEagerReads() {
        var host = mock(GameLoopTitleCardLifecycle.Host.class);
        new GameLoopTitleCardLifecycle(host); new GameLoopTitleCardLifecycle(null);
        verifyNoInteractions(host);
    }
    @Test void pendingOverlayStillTraversesTheVirtualLockSeam() {
        when(level.consumeInLevelTitleCardRequest()).thenReturn(true);
        when(level.consumeInLevelTitleCardPlayerControlLockRequest()).thenReturn(true);
        loop.startPendingInLevelTitleCard(); verify(title).initializeInLevel(0,0); assertEquals(1,loop.locks);
    }
    @Test void nullIsRetriedAndReentrantOuterAssignmentWins() {
        provider(loop, null); AtomicInteger calls = new AtomicInteger();
        when(module.getTitleCardProvider()).thenAnswer(c -> {
            int n = calls.incrementAndGet();
            if (n == 1) return null;
            if (n == 2) { assertSame(title, loop.getTitleCardProvider()); return null; }
            return title;
        });
        assertNull(loop.getTitleCardProvider()); assertNull(loop.getTitleCardProvider()); assertNull(cached(loop));
        assertSame(title, loop.getTitleCardProvider()); assertEquals(4, calls.get());
    }
    @Test void cacheSurvivesBindingLossRefreshAndModuleResetClearsOnlyCache() {
        assertSame(title, loop.getTitleCardProvider());
        destination(loop, PostTitleCardDestination.BONUS_STAGE);
        loop.setGameplayMode(null); assertSame(title, cached(loop));
        loop.setGameplayMode(context); assertSame(title, cached(loop));
        loop.resetModuleScopedProviders(); assertNull(cached(loop));
        assertEquals(PostTitleCardDestination.BONUS_STAGE, destination(loop));
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, result(loop)); verify(title, never()).reset();
        assertSame(title, loop.getTitleCardProvider());
    }
    @Test void internalAcquisitionBypassesPublicGetterAndRepeatsNullCallSites() {
        provider(loop, null); when(module.getTitleCardProvider()).thenReturn(title);
        loop.enterTitleCard(2, 1); assertEquals(0, loop.publicGets);
        verify(module).getTitleCardProvider(); verify(title).initialize(2, 1);
        provider(loop, null); mode(GameMode.LEVEL); when(module.getTitleCardProvider()).thenReturn(null);
        loop.enterTitleCard(3, 0); verify(module, times(2)).getTitleCardProvider();
    }
    @Test void entryCapturesFreshBeforeVirtualModeChangeAndObservesLaterBindings() {
        var later = mock(SpriteManager.class); var p = mock(AbstractPlayableSprite.class);
        when(later.getSprite("sonic")).thenReturn(p);
        when(level.hasPendingFreshLevelTransitionBoundary()).thenReturn(true);
        loop.entering = () -> { when(level.hasPendingFreshLevelTransitionBoundary()).thenReturn(false); raw("spriteManager", later); };
        loop.enterTitleCard(4, 1);
        verify(title).initializeFreshLevelTransition(4, 1); verify(p, never()).setXSpeed(anyShort());
        verify(camera, never()).updatePosition(true); assertEquals(1, loop.characters); assertEquals(1, loop.locks);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pendingTitleTailUsesLevelReboundDuringEntryOrHardwareScan(boolean duringHardwareScan) {
        mode(GameMode.LEVEL);
        when(level.consumeTitleCardRequest()).thenReturn(true);
        when(level.getTitleCardZone()).thenReturn(4);
        when(level.getTitleCardAct()).thenReturn(1);
        when(title.shouldAdvanceVblankClockDuringLockedPhase()).thenReturn(true);
        var rebound = mock(LevelManager.class);
        var gameplay = mock(GameplayModeContext.class);
        var world = mock(WorldSession.class);
        when(world.getGameModule()).thenReturn(module);
        when(gameplay.getWorldSession()).thenReturn(world);
        when(gameplay.hardwareTiming()).thenReturn(mock(com.openggf.game.timing.HardwareTimingService.class));
        raw("gameplayMode", gameplay);
        var frame = (PlcLifecycleFrame) raw("activePlcLifecycleFrame");
        var phase = com.openggf.game.resources.PlcLifecyclePhase.LEVEL_TITLE_CARD;
        when(frame.isOwnedBy(phase)).thenReturn(true);
        when(frame.claim(phase)).thenReturn(true);
        Runnable rebind = () -> raw("levelManager", rebound);
        if (duringHardwareScan) {
            doAnswer(c -> { rebind.run(); return null; }).when(frame).prepareAfterLoop(phase);
        } else {
            loop.entering = rebind;
        }

        assertTrue(loop.presentPendingTitleCardDuringSuppressedRunRow());

        verify(title).initialize(4, 1);
        verify(frame).prepareAfterLoop(phase);
        verify(level, never()).advanceTitleCardVblankOnly();
        verify(rebound).advanceTitleCardVblankOnly();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void ordinaryEntryResetsMainAndLocksAllWhileFrozenCameraIsRetained(boolean frozen) {
        var main = mock(AbstractPlayableSprite.class); var side = mock(AbstractPlayableSprite.class);
        when(sprites.getSprite("sonic")).thenReturn(main); when(sprites.getAllSprites()).thenReturn(List.of(main, side));
        when(camera.getFrozen()).thenReturn(frozen); loop.enterTitleCard(0, 0);
        verify(main).setXSpeed((short)0); verify(main).setYSpeed((short)0); verify(main).setGSpeed((short)0);
        verify(main).setDead(false); verify(main).setHurt(false); verify(main).setRolling(false);
        verify(main).setDeathCountdown(0); verify(main).setInvulnerableFrames(0); verify(main).setAir(false);
        verify(main).setControlLocked(true); verify(side).setControlLocked(true);
        verify(side, never()).setXSpeed(anyShort()); verify(camera, times(frozen ? 0 : 1)).updatePosition(true);
    }
    @Test void initializationFailureKeepsModeAndLocksAndStopsCameraAndListener() {
        var error = new IllegalArgumentException("initialize"); doThrow(error).when(title).initialize(0, 0);
        var listener = mock(GameLoop.GameModeChangeListener.class); loop.setGameModeChangeListener(listener);
        assertSame(error, assertThrows(IllegalArgumentException.class, () -> loop.enterTitleCard(0, 0)));
        assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode()); assertEquals(1, loop.locks);
        verifyNoInteractions(camera, listener);
    }
    @Test void bonusEntryBoundaryAndVirtualModeSeamsAreRetained() {
        assertEquals(GameMode.LEVEL, loop.enterBonusTitleCardAfterLevelLoadBoundary());
        assertEquals(1, loop.bonusBoundaries); assertEquals(PostTitleCardDestination.BONUS_STAGE, destination(loop));
        assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode());
    }
    @Test void acquisitionFailurePreservesIdentityAndNullCache() {
        provider(loop, null); var error = new IllegalStateException("acquire");
        when(module.getTitleCardProvider()).thenThrow(error);
        assertSame(error, assertThrows(IllegalStateException.class, loop::getTitleCardProvider)); assertNull(cached(loop));
    }
    @Test void initializeCallbackRebindsLaterCameraAndListenerAndChangesLiveMode() {
        var later = mock(Camera.class); var listener = mock(GameLoop.GameModeChangeListener.class);
        doAnswer(c -> { raw("camera", later); loop.setGameModeChangeListener(listener); mode(GameMode.TITLE_SCREEN); return null; }).when(title).initialize(0,0);
        loop.enterTitleCard(0,0); verify(later).updatePosition(true); verify(camera, never()).updatePosition(true);
        verify(listener).onGameModeChanged(GameMode.LEVEL, GameMode.TITLE_SCREEN);
    }
    @Test void secondPlayableLockFailureRetainsTheFirstLockAndPublishedMode() {
        var first = mock(AbstractPlayableSprite.class); var second = mock(AbstractPlayableSprite.class);
        when(sprites.getAllSprites()).thenReturn(List.of(first,second)); var error = new IllegalStateException("lock");
        doThrow(error).when(second).setControlLocked(true);
        assertSame(error, assertThrows(IllegalStateException.class, () -> loop.enterTitleCard(0,0)));
        verify(first).setControlLocked(true); verify(first, never()).setControlLocked(false);
        assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode()); verify(title, never()).initialize(anyInt(),anyInt()); verifyNoInteractions(camera);
    }
    @Test void nonTitleReleaseIsColdAndHasNoEffects() throws Exception {
        release(loop); assertEquals(0, loop.locks); verifyNoInteractions(title, events);
    }
    @Test void bonusWinsPendingSpecialReturnAndClearsBeforeModeAndDeferredSetup() throws Exception {
        titleMode(); returning(true); destination(loop, PostTitleCardDestination.BONUS_STAGE);
        loop.entering = () -> assertEquals(PostTitleCardDestination.LEVEL, destination(loop));
        deferred(() -> assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode()));
        release(loop); assertTrue(returning()); assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode());
        assertEquals(0, loop.players); verifyNoInteractions(events); assertFalse((boolean)raw("titleCardReleasedIntoLevelThisIteration"));
    }
    @Test void unlockCanRebindDestinationAndChangeModeWithoutASecondGuard() throws Exception {
        titleMode(); loop.unlock = () -> { destination(loop, PostTitleCardDestination.BONUS_STAGE); mode(GameMode.TITLE_SCREEN); };
        release(loop); assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode());
    }
    @Test void specialReturnConsumesOnlyAfterSuccessfulVirtualModeChange() throws Exception {
        titleMode(); returning(true); var error = new IllegalStateException("mode");
        loop.entering = () -> { throw error; };
        assertSame(error, assertThrows(IllegalStateException.class, () -> release(loop))); assertTrue(returning());
        titleMode(); loop.entering = () -> {}; release(loop); assertFalse(returning());
        verifyNoInteractions(events); assertEquals(0, loop.players);
    }
    @Test void ordinaryReleaseResolvesVirtualPlayerAndCurrentModuleEventsAfterShield() throws Exception {
        titleMode(); var next = mock(LevelEventProvider.class); var newModule = mock(GameModule.class);
        when(newModule.getLevelEventProvider()).thenReturn(next);
        loop.shield = () -> { when(level.hasPendingFreshLevelTransitionBoundary()).thenReturn(true); };
        try (var services = mockStatic(GameServices.class, CALLS_REAL_METHODS)) {
            services.when(GameServices::module).thenReturn(newModule);
            release(loop); verify(newModule).getLevelEventProvider(); verifyNoInteractions(next);
        }
        assertEquals(1, loop.players); assertTrue((boolean)raw("titleCardReleasedIntoLevelThisIteration"));
    }
    @Test void ordinaryCallbackAndListenerObserveLiveModeAndBindings() throws Exception {
        titleMode(); var later = mock(GameLoop.GameModeChangeListener.class);
        doAnswer(c -> { mode(GameMode.TITLE_SCREEN); loop.setGameModeChangeListener(later); return null; }).when(events).applyZonePlayerStateAfterTitleCard();
        release(loop); verify(later).onGameModeChanged(GameMode.TITLE_CARD, GameMode.TITLE_SCREEN);
        assertFalse((boolean)raw("titleCardReleasedIntoLevelThisIteration"));
    }
    @Test void bonusDeferredFailureKeepsClearedDestinationAndDoesNotConsumeSpecialReturn() {
        titleMode(); returning(true); destination(loop, PostTitleCardDestination.BONUS_STAGE);
        var error = new IllegalStateException("setup"); deferred(() -> { throw error; });
        assertSame(error, assertThrows(IllegalStateException.class, () -> release(loop)));
        assertEquals(PostTitleCardDestination.LEVEL, destination(loop)); assertTrue(returning());
        assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode());
    }
    @Test void unlockFailureLeavesDestinationAndModeUntouched() {
        titleMode(); destination(loop, PostTitleCardDestination.BONUS_STAGE);
        var error = new IllegalStateException("unlock"); loop.unlock = () -> { throw error; };
        assertSame(error, assertThrows(IllegalStateException.class, () -> release(loop)));
        assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode()); assertEquals(PostTitleCardDestination.BONUS_STAGE, destination(loop));
    }
    @Test void shieldFailureLeavesModePublishedButSkipsEventsAndListener() {
        titleMode(); var error = new IllegalStateException("shield"); loop.shield = () -> { throw error; };
        assertSame(error, assertThrows(IllegalStateException.class, () -> release(loop)));
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode()); verifyNoInteractions(events);
        assertFalse((boolean)raw("titleCardReleasedIntoLevelThisIteration"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothReleaseEdgesPublishResultOnlyAfterListenerAndAssembly(boolean tail) throws Exception {
        titleMode(); TitleCardProvider p = tail ? mock(TitleCardProvider.class, withSettings().extraInterfaces(TitleCardLoopTail.class)) : title;
        provider(loop, p); when(p.shouldCompleteFreshLevelTransitionBoundary()).thenReturn(!tail); when(p.shouldReleaseControl()).thenReturn(tail);
        loop.setGameModeChangeListener((a,b) -> assertEquals(LevelFrameResult.GAMEPLAY_FRAME, result(loop)));
        assertTrue(update(loop)); assertEquals(LevelFrameResult.SETUP_ONLY, result(loop));
        var order = inOrder(level, sprites);
        order.verify(sprites).setFrameCounter(0); order.verify(level).completeInitialTitleCardPresentation();
        loop.setGameModeChangeListener(null); loop.resetModuleScopedProviders(); mode(GameMode.LEVEL); loop.enterTitleCard(0, 0);
        assertEquals(LevelFrameResult.SETUP_ONLY, result(loop));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void listenerFailurePreventsResultStoreOnEitherReleaseEdge(boolean tail) {
        titleMode(); TitleCardProvider p = tail ? mock(TitleCardProvider.class, withSettings().extraInterfaces(TitleCardLoopTail.class)) : title;
        provider(loop, p); when(p.shouldCompleteFreshLevelTransitionBoundary()).thenReturn(!tail); when(p.shouldReleaseControl()).thenReturn(tail);
        var error = new IllegalStateException("listener"); loop.setGameModeChangeListener((a,b) -> { throw error; });
        assertSame(error, assertThrows(IllegalStateException.class, () -> update(loop)));
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, result(loop)); assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
    }
    @Test void acquisitionFinishesBeforeUpdateSnapshotsAndExitUsesLiveDestination() throws Exception {
        titleMode(); provider(loop, null); var capturedLevel = mock(LevelManager.class); var liveLevel = mock(LevelManager.class);
        var capturedSprites = mock(SpriteManager.class); when(capturedSprites.getAllSprites()).thenReturn(List.of());
        when(module.getTitleCardProvider()).thenAnswer(c -> { raw("levelManager", capturedLevel); raw("spriteManager", capturedSprites); return title; });
        when(title.shouldCompleteFreshLevelTransitionBoundary()).thenReturn(true);
        doAnswer(c -> { destination(loop, PostTitleCardDestination.BONUS_STAGE); raw("levelManager", liveLevel); return null; }).when(title).update();
        assertTrue(update(loop)); verify(capturedLevel).completeInitialTitleCardPresentation(); verify(capturedSprites).setFrameCounter(0);
        verify(liveLevel, never()).completeInitialTitleCardPresentation(); assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode());
    }
    @Test void neutralPreludeRestoresCapturedMasksAfterRebindingAndSentinelFailure() {
        var a = mock(AbstractPlayableSprite.class); var b = mock(AbstractPlayableSprite.class); var rebound = mock(SpriteManager.class);
        when(sprites.getAllSprites()).thenReturn(List.of(a,b)); when(a.getForcedInputMask()).thenReturn(7); when(b.getForcedInputMask()).thenReturn(9);
        doAnswer(c -> { raw("spriteManager", rebound); return null; }).when(b).clearForcedInputMask();
        var error = new IllegalStateException("update"); doThrow(error).when(rebound).update(input);
        assertSame(error, assertThrows(IllegalStateException.class, () -> prelude(loop)));
        var order = inOrder(a,b,rebound); order.verify(a).clearForcedInputMask(); order.verify(b).clearForcedInputMask();
        order.verify(rebound).update(input); order.verify(a).setForcedInputMask(7); order.verify(b).setForcedInputMask(9);
        verify(sprites, never()).update(input);
    }
    @Test void coldPacingAndPresentationObservationsDoNotAcquireProvider() throws Exception {
        provider(loop, null);
        SpecialStageLifecycleTestAccess.call(loop, "canUseGameplayPacingOutsideRewind", new Class<?>[0]);
        loop.pause(); loop.stepPresentationFrame(); loop.resume();
        assertNull(cached(loop)); verify(module, never()).getTitleCardProvider();
    }
    @Test void cacheAndDestinationSurviveRealParkResumeAndTeardown() {
        destination(loop, PostTitleCardDestination.BONUS_STAGE);
        var old = loop.suspension.park(); assertSame(title, cached(loop));
        loop.suspension.unpark(old); assertSame(title, cached(loop));
        loop.tearDownGameplayForReturn(); assertSame(title, cached(loop));
        assertEquals(PostTitleCardDestination.BONUS_STAGE, destination(loop));
    }
    @Test void resetClearsCacheOnlyAfterContinueResetSuccessfullyReturns() {
        var coordinator = mock(GameLoopContinueCoordinator.class); raw("continueScreen", coordinator);
        var error = new IllegalStateException("continue reset");
        doAnswer(c -> { assertSame(title, cached(loop)); throw error; }).when(coordinator).reset();
        assertSame(error, assertThrows(IllegalStateException.class, loop::resetModuleScopedProviders)); assertSame(title, cached(loop));
        doNothing().when(coordinator).reset(); loop.resetModuleScopedProviders(); assertNull(cached(loop)); verify(title, never()).reset();
    }
    @Test void twoSeparateLiveLevelChecksSeeSynchronizationModeChange() throws Exception {
        titleMode(); var bridge = mock(com.openggf.debug.playback.PlaybackInputBridge.class); raw("playbackInputBridge", bridge);
        doAnswer(c -> { mode(GameMode.BONUS_STAGE); return null; }).when(bridge).sync(any(), eq(GameMode.LEVEL), any(), any());
        release(loop); verify(bridge).sync(any(), eq(GameMode.LEVEL), same(input), same(sprites));
        assertFalse((boolean)raw("titleCardReleasedIntoLevelThisIteration"));
    }
    @Test void bonusSynchronizationIsUnconditionalAndThenLiveLevelTailCanAlsoRun() throws Exception {
        titleMode(); destination(loop, PostTitleCardDestination.BONUS_STAGE);
        var bridge = mock(com.openggf.debug.playback.PlaybackInputBridge.class); raw("playbackInputBridge", bridge);
        deferred(() -> mode(GameMode.LEVEL)); release(loop);
        verify(bridge, times(2)).sync(any(), eq(GameMode.LEVEL), same(input), same(sprites));
        assertTrue((boolean)raw("titleCardReleasedIntoLevelThisIteration"));
    }
    @Test void plcPreparationReadsTheReboundFrameAfterCapturedProviderUpdate() throws Exception {
        titleMode(); var before = (PlcLifecycleFrame) raw("activePlcLifecycleFrame"); var after = mock(PlcLifecycleFrame.class);
        when(after.isOwnedBy(com.openggf.game.resources.PlcLifecyclePhase.LEVEL_TITLE_CARD)).thenReturn(true);
        doAnswer(c -> { raw("activePlcLifecycleFrame", after); return null; }).when(title).update();
        assertFalse(update(loop));
        verify(before).claim(com.openggf.game.resources.PlcLifecyclePhase.LEVEL_TITLE_CARD);
        verify(after).prepareAfterLoop(com.openggf.game.resources.PlcLifecyclePhase.LEVEL_TITLE_CARD);
    }
    @Test void freshPublicationPrecedesProviderHandoffAndDestinationSetup() throws Exception {
        titleMode(); when(level.hasPendingFreshLevelTransitionBoundary()).thenReturn(true);
        when(title.shouldCompleteFreshLevelTransitionBoundary()).thenReturn(true);
        assertTrue(update(loop)); var order = inOrder(level,title);
        order.verify(level).publishFreshLevelTransitionInitialBoundary(); order.verify(title).completeFreshLevelRuntimeArtHandoff();
        order.verify(level).completeInitialTitleCardPresentation(); verify(events, never()).applyZonePlayerStateAfterTitleCard();
    }
    @Test void listenerFailurePreventsEnumAssemblyFloorAndSuccessfulReleaseOrdersItLast() throws Exception {
        titleMode(); when(title.shouldCompleteFreshLevelTransitionBoundary()).thenReturn(true);
        try (var boundaries = mockStatic(com.openggf.level.LevelRewindBoundaryAccess.class)) {
            var error = new IllegalStateException("listener floor"); loop.setGameModeChangeListener((a,b) -> { throw error; });
            assertSame(error, assertThrows(IllegalStateException.class, () -> update(loop)));
            boundaries.verifyNoInteractions(); titleMode();
            loop.setGameModeChangeListener((a,b) -> boundaries.verifyNoInteractions());
            assertTrue(update(loop)); boundaries.verify(() -> com.openggf.level.LevelRewindBoundaryAccess.initialAssemblyCompleted(level));
        }
    }

    @Test void maskClearFailureOccursBeforeFinallyAndRetainsPartialEffects() {
        var a = mock(AbstractPlayableSprite.class); var b = mock(AbstractPlayableSprite.class);
        when(sprites.getAllSprites()).thenReturn(List.of(a,b)); var error = new IllegalStateException("clear");
        doThrow(error).when(b).clearForcedInputMask();
        assertSame(error, assertThrows(IllegalStateException.class, () -> prelude(loop)));
        verify(a).clearForcedInputMask(); verify(a, never()).setForcedInputMask(anyInt()); verify(b, never()).setForcedInputMask(anyInt());
        verify(sprites, never()).update(input);
    }
}
