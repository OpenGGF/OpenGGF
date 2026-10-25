package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.*;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.*;
import com.openggf.graphics.FadeManager;
import com.openggf.level.*;
import com.openggf.level.objects.ObjectCallbackAbortException;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.openggf.BonusStageSessionTestAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Preservation controls first executed against unchanged bonus production at 850948ad9. */
@Isolated
class TestGameLoopBonusStageSession {
    private GameLoop loop;
    private GameModule module;
    private BonusStageProvider provider;
    private AudioManager audio;
    private LevelManager levels;
    private SpriteManager sprites;
    private FadeManager fade;
    private TitleCardProvider title;
    private AbstractPlayableSprite player;

    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.resetPerTest();
        SessionManager.clear();
        module = mock(GameModule.class);
        when(module.getRules()).thenReturn(GameRules.SONIC_2);
        when(module.rngFlavour()).thenReturn(GameRng.Flavour.S1_S2);
        when(module.createRuntimeArtCoordinator(any())).thenReturn(RuntimeArtCoordinator.NONE);
        SessionManager.openGameplaySession(module);
        TestEnvironment.activeGameplayMode();
        audio = mock(AudioManager.class);
        var old = EngineServices.current();
        var engine = new EngineContext(old.configuration(), old.graphics(), audio, old.roms(), old.profiler(),
                old.debugOverlay(), old.playbackDebug(), old.romDetection(), old.crossGameFeatures(), old.moduleResolutionService());
        var input = mock(InputHandler.class);
        when(input.logical()).thenReturn(LogicalInputSnapshot.neutral());
        loop = new GameLoop(engine, input);
        levels = mock(LevelManager.class); sprites = mock(SpriteManager.class); fade = mock(FadeManager.class);
        title = mock(TitleCardProvider.class); player = mock(AbstractPlayableSprite.class);
        when(sprites.getSprite(anyString())).thenReturn(player);
        when(sprites.getAllSprites()).thenReturn(List.of(player));
        write(loop, "levelManager", levels); write(loop, "spriteManager", sprites);
        write(loop, "camera", mock(Camera.class)); write(loop, "fadeManager", fade); com.openggf.TitleCardLifecycleTestAccess.provider(loop, title);
        provider = mock(BonusStageProvider.class);
        when(provider.hasBonusStages()).thenReturn(true);
        when(provider.getRewards()).thenReturn(BonusStageProvider.BonusStageRewards.none());
        when(module.getBonusStageProvider()).thenReturn(provider);
        clearInvocations(module, audio);
    }
    @AfterEach void cleanup() { SessionManager.clear(); }
    private static BonusStageState state(int rings) { return new BonusStageState(0x0102, 0x0304, rings, 0, 0, 0, 0, 0, 12, 34, 0, 0, (byte)0, (byte)0, 0, 123L, 0); }
    private void mode(GameMode mode) { write(loop, "currentGameMode", mode); }
    private AtomicReference<Runnable> entryFade() {
        var callback = new AtomicReference<Runnable>();
        doAnswer(c -> { callback.set(c.getArgument(0)); return null; }).when(fade).startFadeToBlack(any());
        return callback;
    }
    private void release() throws Exception { TitleCardLifecycleTestAccess.release(loop); }

    @Test void admissionShortCircuitsBeforeCaptureOrPresentation() throws Exception {
        mode(GameMode.BONUS_STAGE); enter(loop, BonusStageType.GUMBALL);
        verifyNoInteractions(module, audio, fade, levels, sprites);
        mode(GameMode.LEVEL);
        when(provider.hasBonusStages()).thenReturn(false);
        enter(loop, null); verify(provider).hasBonusStages(); verifyNoInteractions(fade, levels, sprites, audio);
        when(provider.hasBonusStages()).thenReturn(true);
        enter(loop, null); enter(loop, BonusStageType.NONE); verifyNoInteractions(fade, levels, sprites, audio);
        when(fade.isActive()).thenReturn(true);
        enter(loop, BonusStageType.GUMBALL); verify(fade).isActive(); verifyNoInteractions(levels, sprites, audio);
        assertFalse(pending(loop));
    }
    @Test void runPolicyRejectsBeforeModuleOrFade() throws Exception {
        var context = mock(GameplayModeContext.class);
        when(context.getRunPolicy()).thenReturn(GameplayRunPolicy.isolatedAct());
        write(loop, "gameplayMode", context);
        enter(loop, BonusStageType.GUMBALL);
        verify(context).getRunPolicy(); verifyNoInteractions(module, fade, levels, audio, sprites);
    }
    @Test void entryCaptureAndMusicPrecedePendingAndFade() throws Exception {
        var callback = entryFade();
        doAnswer(c -> { assertFalse(pending(loop)); return null; }).when(audio).fadeOutMusic();
        doAnswer(c -> { assertTrue(pending(loop)); callback.set(c.getArgument(0)); return null; }).when(fade).startFadeToBlack(any());
        enter(loop, BonusStageType.GUMBALL);
        var order = inOrder(provider, fade, levels, audio);
        order.verify(provider).hasBonusStages(); order.verify(fade).isActive(); order.verify(levels).getCheckpointState();
        order.verify(audio).fadeOutMusic(); order.verify(fade).startFadeToBlack(any());
        verify(provider, never()).onEnter(any(), any()); assertNotNull(callback.get());
    }
    @Test void entryKeepsCapturedPublishedContextAcrossOnEnterAndLoadRebinding() throws Exception {
        var first = mock(GameplayModeContext.class); var later = mock(GameplayModeContext.class);
        var current = new AtomicReference<>(first); var rebound = mock(LevelManager.class);
        try (var sessions = mockStatic(SessionManager.class, CALLS_REAL_METHODS)) {
            sessions.when(SessionManager::getCurrentGameplayMode).thenAnswer(c -> current.get());
            doAnswer(c -> { current.set(later); return null; }).when(provider).onEnter(any(), any());
            doAnswer(c -> { write(loop, "levelManager", rebound); return null; }).when(levels).loadZoneAndAct(anyInt(), anyInt());
            entered(loop, provider, BonusStageType.GUMBALL, null);
            var order = inOrder(first, provider, levels, rebound, title, audio, fade);
            order.verify(first).setActiveBonusStageProvider(provider); order.verify(provider).onEnter(BonusStageType.GUMBALL, null);
            order.verify(first).registerBonusStageAdapter(provider); order.verify(provider).getZoneId(BonusStageType.GUMBALL);
            order.verify(levels).loadZoneAndAct(anyInt(), anyInt()); order.verify(rebound).consumeTitleCardRequest();
            order.verify(rebound).setBonusStageHudLayout(true); order.verify(title).initializeBonus();
            order.verify(audio).playMusic(0); order.verify(fade).cancel();
            verify(later, never()).setActiveBonusStageProvider(any()); verify(later, never()).registerBonusStageAdapter(any());
        }
    }
    @Test void ordinaryEntryIoCleansCapturedContextEvenWhenLoadRebinds() throws Exception {
        var first = mock(GameplayModeContext.class); var later = mock(GameplayModeContext.class); var current = new AtomicReference<>(first);
        try (var sessions = mockStatic(SessionManager.class, CALLS_REAL_METHODS)) {
            sessions.when(SessionManager::getCurrentGameplayMode).thenAnswer(c -> current.get());
            doAnswer(c -> { current.set(later); throw new IOException("ordinary"); }).when(levels).loadZoneAndAct(anyInt(), anyInt());
            entered(loop, provider, BonusStageType.GUMBALL, null);
            var order = inOrder(first, provider);
            order.verify(first).setActiveBonusStageProvider(provider); order.verify(provider).onEnter(any(), any());
            order.verify(first).registerBonusStageAdapter(provider); order.verify(provider).onExit();
            order.verify(first).setActiveBonusStageProvider(null); order.verify(first).deregisterBonusStageAdapter();
            verifyNoInteractions(later); assertNull(provider(loop)); assertFalse(pending(loop)); assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        }
    }
    private static final class Abort extends ObjectCallbackAbortException { Abort() { super("abort", null); } }
    private static IOException trusted(Abort abort) throws Exception {
        var constructor = DeferredLevelLoadException.class.getDeclaredConstructor(ObjectCallbackAbortException.class);
        constructor.setAccessible(true); return constructor.newInstance(abort);
    }
    @Test void trustedEntryAbortEscapesByExactIdentityBeforeIoCleanup() throws Exception {
        var abort = new Abort(); var carrier = trusted(abort); var context = mock(GameplayModeContext.class);
        doThrow(carrier).when(levels).loadZoneAndAct(anyInt(), anyInt());
        try (var sessions = mockStatic(SessionManager.class, CALLS_REAL_METHODS)) {
            sessions.when(SessionManager::getCurrentGameplayMode).thenReturn(context);
            pending(loop, true); loop.setPendingBonusStageShieldRestoreForTest(ShieldType.FIRE);
            assertSame(abort, assertThrows(Abort.class, () -> entered(loop, provider, BonusStageType.GUMBALL, state(9))));
            assertSame(provider, provider(loop)); assertFalse(pending(loop)); assertFalse(loop.hasPendingBonusStageShieldRestoreForTest());
            verify(context).registerBonusStageAdapter(provider); verify(context, never()).deregisterBonusStageAdapter();
            verify(provider, never()).onExit(); verify(title, never()).initializeBonus(); assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void allReturnIoFailuresKeepWrapperAndThenCurrentFinallyBinding(boolean normal) throws Exception {
        var abort = new Abort(); var carrier = trusted(abort); var rebound = mock(LevelManager.class);
        var context = mock(GameplayModeContext.class);
        when(context.runLevelLoads()).thenReturn(new com.openggf.trace.replay.runs.RunLevelLoadTracker());
        doAnswer(c -> { write(loop, "levelManager", rebound); throw carrier; }).when(levels).loadZoneAndAct(anyInt(), anyInt());
        try (var sessions = mockStatic(SessionManager.class, CALLS_REAL_METHODS)) {
            sessions.when(SessionManager::getCurrentGameplayMode).thenReturn(context);
            mode(GameMode.BONUS_STAGE); provider(loop, provider); pending(loop, true);
            var failure = assertThrows(RuntimeException.class, () -> exited(loop, provider, normal ? state(10) : null));
            assertEquals(RuntimeException.class, failure.getClass()); assertSame(carrier, failure.getCause());
            assertEquals(normal ? "Failed to reload level after bonus stage" : "Failed to load fallback level", failure.getMessage());
            assertSame(abort, DeferredLevelLoadException.callbackAbortInLoadFailure(failure));
            assertNull(provider(loop)); assertFalse(pending(loop)); assertEquals(GameMode.BONUS_STAGE, loop.getCurrentGameMode());
            verify(context).setActiveBonusStageProvider(null); verify(context).deregisterBonusStageAdapter(); verify(levels).setBonusStageHudLayout(false);
            verify(rebound, times(normal ? 1 : 0)).clearBonusStageReturn(); verify(levels, never()).clearBonusStageReturn();
            verify(title, never()).initialize(anyInt(), anyInt()); verify(fade, never()).startFadeFromBlack(any());
        }
    }
    @Test void entryCallbackFailureRetainsPublicationWithoutRegistrationOrLoad() throws Exception {
        var context = mock(GameplayModeContext.class); var failure = new IllegalStateException("enter");
        try (var sessions = mockStatic(SessionManager.class, CALLS_REAL_METHODS)) {
            sessions.when(SessionManager::getCurrentGameplayMode).thenReturn(context);
            doThrow(failure).when(provider).onEnter(any(), any()); pending(loop, true);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> entered(loop, provider, BonusStageType.GUMBALL, null)));
            assertSame(provider, provider(loop)); assertFalse(pending(loop)); verify(context).setActiveBonusStageProvider(provider);
            verify(context, never()).registerBonusStageAdapter(any()); verifyNoInteractions(levels);
        }
    }
    @Test void deferredProviderIsIndependentAndClearedBeforeThrowWithoutReplay() throws Exception {
        var replacement = mock(BonusStageProvider.class); var failure = new IllegalStateException("deferred");
        deferred(loop, provider); provider(loop, replacement);
        doAnswer(c -> { assertNull(deferred(loop)); throw failure; }).when(provider).onDeferredSetupComplete();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> completeDeferred(loop)));
        completeDeferred(loop); verify(provider, times(1)).onDeferredSetupComplete(); verifyNoInteractions(replacement);
    }
    @Test void exitCapturesSavedStateBeforeFadeButLiveRingsAndRewardsAtCompletion() throws Exception {
        var callback = entryFade(); mode(GameMode.BONUS_STAGE); provider(loop, provider);
        var saved = state(11); var live = mock(LevelState.class);
        when(levels.getLevelGamestate()).thenReturn(live); when(live.getRings()).thenReturn(69);
        when(provider.getSavedState()).thenReturn(saved);
        exit(loop); verify(provider).getSavedState(); verify(provider, never()).getRewards(); verify(provider, never()).onExit();
        when(provider.getSavedState()).thenReturn(state(900));
        when(provider.getRewards()).thenReturn(new BonusStageProvider.BonusStageRewards(200, 0, false, false, false, false));
        callback.get().run();
        var order = inOrder(live, provider); order.verify(live).getRings(); order.verify(provider).getRewards(); order.verify(provider).onExit();
        verify(live).setRings(69); verify(live, never()).setRings(211); verify(levels).loadZoneAndAct(1, 2);
        verify(title).initialize(3, 4); verify(provider, times(1)).getSavedState();
    }
    @Test void exitLooksUpPublishedContextAfterOnExitAndRetainsPartialStateIfItThrows() throws Exception {
        var first = mock(GameplayModeContext.class); var later = mock(GameplayModeContext.class); var current = new AtomicReference<>(first);
        try (var sessions = mockStatic(SessionManager.class, CALLS_REAL_METHODS)) {
            sessions.when(SessionManager::getCurrentGameplayMode).thenAnswer(c -> current.get());
            doAnswer(c -> { current.set(later); return null; }).when(provider).onExit();
            exited(loop, provider, null); verifyNoInteractions(first); verify(later).setActiveBonusStageProvider(null); verify(later).deregisterBonusStageAdapter();
            clearInvocations(levels, later); var failure = new IllegalStateException("exit"); doThrow(failure).when(provider).onExit();
            provider(loop, provider); pending(loop, true);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> exited(loop, provider, null)));
            assertSame(provider, provider(loop)); assertFalse(pending(loop)); verifyNoInteractions(later); verify(levels, never()).setBonusStageHudLayout(false);
        }
    }
    @Test void secondShieldRetainsOnNullAndClearsBeforeGiveAndBeforeZoneState() throws Exception {
        loop.setPendingBonusStageShieldRestoreForTest(ShieldType.BUBBLE);
        loop.applyPendingBonusStageShieldRestore(null); assertTrue(loop.hasPendingBonusStageShieldRestoreForTest());
        var events = mock(LevelEventProvider.class); when(module.getLevelEventProvider()).thenReturn(events);
        doAnswer(c -> { assertFalse(loop.hasPendingBonusStageShieldRestoreForTest()); return null; }).when(player).giveShield(ShieldType.BUBBLE);
        mode(GameMode.TITLE_CARD); release();
        var order = inOrder(player, events); order.verify(player).giveShield(ShieldType.BUBBLE); order.verify(events).applyZonePlayerStateAfterTitleCard();
        loop.applyPendingBonusStageShieldRestore(player); verify(player, times(1)).giveShield(ShieldType.BUBBLE);
    }
    @Test void lifeAwardsKeepTheBoundGameStateReceiverAcrossRebinding() throws Exception {
        var first = mock(GameStateManager.class); var later = mock(GameStateManager.class); write(loop, "gameState", first);
        when(provider.getRewards()).thenReturn(new BonusStageProvider.BonusStageRewards(0, 2, false, false, false, false));
        doAnswer(c -> { write(loop, "gameState", later); return null; }).when(player).setTopSolidBit(anyByte());
        exited(loop, provider, state(9)); verify(first, times(2)).addLife(); verify(later, never()).addLife();
    }
    @Test void frameKeepsExecutedProviderOwnerAndModeButUsesLivePriorityAndCompletion() throws Exception {
        var executedOwner = mock(GameplayModeContext.class); var reboundOwner = mock(GameplayModeContext.class);
        var replacement = mock(BonusStageProvider.class, withSettings().extraInterfaces(BonusStagePlayerPriorityPolicy.class));
        when(((BonusStagePlayerPriorityPolicy) replacement).shouldForcePlayerHighPriority()).thenReturn(false);
        when(provider.supportsRewind()).thenReturn(true);
        var rewind = mock(com.openggf.game.rewind.LiveRewindManager.class);
        var playback = mock(com.openggf.debug.playback.PlaybackDebugManager.class);
        var frame = mock(com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame.class);
        var context = mock(LevelFrameContext.class);
        write(loop, "liveRewindManager", rewind); write(loop, "playbackDebugManager", playback);
        write(loop, "gameplayMode", executedOwner); write(loop, "activePlcLifecycleFrame", frame);
        mode(GameMode.BONUS_STAGE); provider(loop, provider);
        try (var contexts = mockStatic(LevelFrameContext.class); var steps = mockStatic(LevelFrameStep.class)) {
            contexts.when(() -> LevelFrameContext.from(executedOwner)).thenReturn(context);
            steps.when(() -> LevelFrameStep.execute(eq(context), eq(frame), any(), eq(levels), any(), any(), any()))
                    .thenAnswer(c -> {
                        provider(loop, replacement); write(loop, "gameplayMode", reboundOwner); mode(GameMode.LEVEL);
                        return LevelFrameResult.GAMEPLAY_FRAME;
                    });
            call(loop, "updateBonusStageMode", new Class<?>[]{boolean.class}, false);
            var order = inOrder(replacement, provider, rewind, playback);
            order.verify((BonusStagePlayerPriorityPolicy) replacement).shouldForcePlayerHighPriority();
            order.verify(provider).onFrameUpdate();
            order.verify(rewind).recordExternalFrame(eq(executedOwner), eq(GameMode.BONUS_STAGE), isNull(), eq(provider), eq(false), any(), eq(false));
            order.verify(replacement).isStageComplete(); order.verify(playback).onLevelFrameAdvanced();
            verify(provider, never()).isStageComplete(); verify(replacement, never()).onFrameUpdate();
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void frozenAndLagRowsServiceVblankAndPlaybackWithoutGameplayOrRewind(boolean frozen) throws Exception {
        var objects = mock(com.openggf.level.objects.ObjectManager.class); when(levels.getObjectManager()).thenReturn(objects);
        var playback = mock(com.openggf.debug.playback.PlaybackDebugManager.class);
        when(playback.shouldSkipCurrentGameplayTick()).thenReturn(true);
        when(playback.currentSkippedTickVblankAdvanceCount()).thenReturn(3);
        var rewind = mock(com.openggf.game.rewind.LiveRewindManager.class);
        var frame = mock(com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame.class);
        var context = mock(LevelFrameContext.class);
        write(loop, "playbackDebugManager", playback); write(loop, "liveRewindManager", rewind); write(loop, "activePlcLifecycleFrame", frame);
        mode(GameMode.BONUS_STAGE); provider(loop, provider); pending(loop, frozen);
        try (var contexts = mockStatic(LevelFrameContext.class); var steps = mockStatic(LevelFrameStep.class)) {
            contexts.when(() -> LevelFrameContext.from(any())).thenReturn(context);
            call(loop, "updateBonusStageMode", new Class<?>[]{boolean.class}, false);
            steps.verify(() -> LevelFrameStep.serviceHardwareVBlankOnly(context, frame), times(frozen ? 1 : 3));
            verify(objects, times(frozen ? 1 : 3)).advanceVblaCounter(); verify(playback).onLevelFrameAdvanced();
            verify(provider, never()).onFrameUpdate(); verify(provider, never()).isStageComplete(); verifyNoInteractions(rewind);
            verify(sprites, never()).update(any());
        }
    }
    @Test void registrationAndZoneSelectionThrowsRetainPublishedProviderWithoutRollback() throws Exception {
        var context = mock(GameplayModeContext.class); var failure = new IllegalStateException("registration");
        try (var sessions = mockStatic(SessionManager.class, CALLS_REAL_METHODS)) {
            sessions.when(SessionManager::getCurrentGameplayMode).thenReturn(context);
            doThrow(failure).when(context).registerBonusStageAdapter(provider);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> entered(loop, provider, BonusStageType.GUMBALL, null)));
            assertSame(provider, provider(loop)); verify(provider, never()).getZoneId(any()); verifyNoInteractions(levels);
            doNothing().when(context).registerBonusStageAdapter(provider); when(provider.getZoneId(any())).thenThrow(failure);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> entered(loop, provider, BonusStageType.GUMBALL, null)));
            verify(provider, never()).onExit(); verify(context, never()).deregisterBonusStageAdapter(); verifyNoInteractions(levels);
        }
    }
    @ParameterizedTest @ValueSource(strings={"title","interior","exit"})
    void parkingRetainsVisitAndDeferredOrFadeObligationsWithoutSteppingWorld(String phase) throws Exception {
        var callback = entryFade(); var saved = state(17);
        provider(loop, provider); when(provider.getSavedState()).thenReturn(saved);
        mode(phase.equals("title") ? GameMode.TITLE_CARD : GameMode.BONUS_STAGE);
        if (phase.equals("exit")) exit(loop); else deferred(loop, provider);
        var suspension = (GameLoopSuspension) read(loop, "suspension");
        var previous = suspension.park();
        assertSame(provider, provider(loop)); assertNull(loop.resolveGameplayModeContext());
        verify(provider, never()).onFrameUpdate(); verify(levels, never()).loadZoneAndAct(anyInt(), anyInt());
        suspension.unpark(previous);
        // Reinstall the deliberately mocked live managers after real binding refresh.
        write(loop, "levelManager", levels); write(loop, "spriteManager", sprites); write(loop, "fadeManager", fade); com.openggf.TitleCardLifecycleTestAccess.provider(loop, title);
        if (phase.equals("exit")) { assertTrue(pending(loop)); callback.get().run(); verify(provider).onExit(); }
        else { completeDeferred(loop); completeDeferred(loop); verify(provider, times(1)).onDeferredSetupComplete(); }
    }
}
