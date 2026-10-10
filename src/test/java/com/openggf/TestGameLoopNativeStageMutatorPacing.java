package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.audio.presentation.OuterFramePresentation;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.internal.NativeSpecialStageFrame;
import com.openggf.game.internal.NativeSpecialStagePacing;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageProvider;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.openggf.data.RomManager;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;

/** Actual stage providers and native bonus level pipeline, driven by the presentation pump. */
@Isolated
class TestGameLoopNativeStageMutatorPacing {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void s1FourNativeTicksOneOuterAudioAndCanonicalSingleTick() throws Exception {
        verifyFourTicks(new Sonic1SpecialStageProvider());
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void s2FourNativeIterationsIncludeLagWithoutMultiplyingOuterAudio() throws Exception {
        verifyFourTicks(new Sonic2SpecialStageProvider());
    }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void s3kFourNativeTicksOneOuterAudioAndCanonicalSingleTick() throws Exception {
        verifyFourTicks(new Sonic3kSpecialStageProvider());
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void zeroStepPauseActsImmediatelyAndStageInputNeverAdvancesWhilePaused() throws Exception {
        var f = fixture(new Sonic3kSpecialStageProvider(), 25);
        long before = ((NativeSpecialStagePacing) f.provider).pacingState().acceptedSampleOrdinal();
        f.input.handleKeyEvent(config().getInt(SonicConfiguration.PAUSE_KEY), GLFW_PRESS);
        f.loop.stepPresentationFrame();
        assertTrue(f.loop.isPaused()); assertEquals(0, f.ticks.get());
        assertEquals(before, ((NativeSpecialStagePacing) f.provider).pacingState().acceptedSampleOrdinal());
        assertFalse(f.pacing.hasPendingInput()); verify(f.audio).pause();
        verify(f.audio).setForwardPlaybackRate(1.0);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sameProviderReinitializeStopsBudgetEvenWhenNativeControlsAreReadyAgain() throws Exception {
        var f = fixture(new Sonic3kSpecialStageProvider(), 400);
        long epoch = ((NativeSpecialStagePacing) f.provider).pacingState().entryEpoch();
        f.loop.afterTick = () -> {
            try { f.provider.reset(); f.provider.initializeStage(0); warm(f.provider); }
            catch (Exception e) { throw new AssertionError(e); }
        };
        f.loop.stepPresentationFrame();
        assertEquals(1, f.ticks.get(), "A same-object new entry cannot inherit the remaining three ticks");
        assertNotEquals(epoch, ((NativeSpecialStagePacing) f.provider).pacingState().entryEpoch());
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void changingToAnotherEligibleGameplayModeStopsOutstandingBudget() throws Exception {
        var f = fixture(new Sonic3kSpecialStageProvider(), 400);
        f.loop.afterTick = () -> f.loop.setGameMode(GameMode.LEVEL);
        f.loop.stepPresentationFrame();
        assertEquals(1, f.ticks.get(), "Eligibility alone cannot carry a stage budget into a different mode");
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void nativeSlotBonusRuntimeUpdatesOncePerExecutedLevelFrameAcrossFourTickBudget() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_SLOT_MACHINE, 0).build();
        var actualContext = SessionManager.getCurrentGameplayMode();
        var provider = spy((Sonic3kBonusStageCoordinator) GameServices.module().getBonusStageProvider());
        actualContext.setActiveBonusStageProvider(provider);
        provider.onEnter(BonusStageType.SLOT_MACHINE, savedState());
        provider.onDeferredSetupComplete();
        assertNotNull(provider.activeSlotRuntime()); assertTrue(provider.updateDuringLevelFrame());
        assertFalse(provider.supportsRewind(), "This change does not add unsupported Slots rewind");
        admitNativeSetup(actualContext);
        var ticks = new AtomicInteger(); var pacing = new GameplayMutatorPacing(() -> policy(400));
        var world = countingWorld(pacing, ticks); var pumpContext = mock(GameplayModeContext.class);
        when(pumpContext.getWorldSession()).thenReturn(world);
        when(pumpContext.isGameplayRuntimeReady()).thenReturn(true);
        when(pumpContext.getActiveBonusStageProvider()).thenReturn(provider);
        var input = new InputHandler(); var loop = new PumpLoop(input); var audio = mock(AudioManager.class);
        install(loop, pumpContext, audio); set(loop, "activeBonusStageProvider", provider);
        set(loop, "camera", GameServices.camera()); loop.setGameMode(GameMode.BONUS_STAGE);
        loop.body = () -> fixture.stepIdleFrames(1);
        int before = GameServices.level().getFrameCounter();
        clearInvocations(provider);
        loop.stepPresentationFrame(); loop.presentOuterFrame(false, false);
        assertEquals(4, GameServices.level().getFrameCounter() - before);
        verify(provider, times(4)).onFrameUpdate();
        verify(audio, times(1)).presentFrame(PresentationMode.FORWARD);
        // The native level pipeline owns integrated updates; the pump does not call the coordinator itself.
    }

    @ParameterizedTest @EnumSource(value = BonusStageType.class, names = {"GUMBALL", "GLOWING_SPHERE"})
    @RequiresRom(SonicGame.SONIC_3K)
    void nativeGumballAndPachinkoExecuteFourOrdinaryBonusTicks(BonusStageType type) throws Exception {
        var rom = GameServices.rom().getRom();
        var pacing = new GameplayMutatorPacing(() -> policy(400));
        var state = new WorldSessionPolicyState() {
            public <T> T getService(Class<T> requested) { return requested == GameplayMutatorPacing.class ? requested.cast(pacing) : null; }
            public RewindSnapshottable<?> rewindAdapter() { return pacing; }
            public void beforeAssembly(LevelLoadCause cause) { }
            public void bindRoster(SpriteManager sprites) { }
            public void failedAssembly(LevelLoadCause cause) { }
            public void closeScreens() { }
            public void retire() { pacing.reset(); }
        };
        var module = new DelegatingGameModule(new Sonic3kGameModule(), "test:native-bonus-pacing") {
            @Override public <T> T getGameService(Class<T> requested) {
                return requested == WorldSessionPolicyProvider.class
                        ? requested.cast((WorldSessionPolicyProvider) world -> state) : super.getGameService(requested);
            }
        };
        TestEnvironment.configureGameModuleFixture(module); RomManager.getInstance().setRom(rom);
        int zone = type == BonusStageType.GUMBALL ? Sonic3kZoneIds.ZONE_GUMBALL : Sonic3kZoneIds.ZONE_GLOWING_SPHERE;
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, 0).build();
        var context = SessionManager.getCurrentGameplayMode();
        var provider = spy((Sonic3kBonusStageCoordinator) module.getBonusStageProvider());
        provider.onEnter(type, savedState()); provider.onDeferredSetupComplete();
        context.setActiveBonusStageProvider(provider);
        assertTrue(provider.supportsRewind()); assertFalse(provider.updateDuringLevelFrame());
        var input = new InputHandler(InputBindingFactory.supplier(config())); var loop = new GameLoop(input);
        set(loop, "activeBonusStageProvider", provider); loop.setGameMode(GameMode.BONUS_STAGE);
        var audio = mock(AudioManager.class); set(loop, "audioManager", audio);
        set(loop, "outerFramePresentation", new OuterFramePresentation(audio));
        int before = GameServices.level().getFrameCounter(); clearInvocations(provider);
        assertFalse(fixture.sprite().getDead());
        assertTrue(GameServices.level().hasPendingInitialProcessSpritesPass());
        loop.stepPresentationFrame();
        assertEquals(before, GameServices.level().getFrameCounter(), "The native one-shot ProcessSprites setup has no gameplay tick");
        assertFalse(GameServices.level().hasPendingInitialProcessSpritesPass());
        assertEquals(0, pacing.capture().remainder());
        verify(provider, times(1)).onFrameUpdate();
        clearInvocations(provider);
        loop.stepPresentationFrame(); loop.presentOuterFrame(false, false);
        assertEquals(4, GameServices.level().getFrameCounter() - before);
        verify(provider, times(4)).onFrameUpdate();
        verify(audio, times(1)).presentFrame(PresentationMode.FORWARD);
    }

    private static void admitNativeSetup(GameplayModeContext context) {
        if (GameServices.level().hasPendingInitialProcessSpritesPass()) {
            assertEquals(LevelFrameResult.SETUP_ONLY,
                    LevelFrameStep.admit(LevelFrameContext.from(context), GameServices.level(), false).result());
        }
        assertFalse(GameServices.level().hasPendingInitialProcessSpritesPass());
    }

    private static void verifyFourTicks(SpecialStageProvider provider) throws Exception {
        var f = fixture(provider, 400);
        f.loop.stepPresentationFrame(); f.loop.presentOuterFrame(false, false);
        assertEquals(4, f.ticks.get()); verify(f.audio, times(1)).presentFrame(PresentationMode.FORWARD);
        verify(f.audio).setForwardPlaybackRate(4.0);
        int before = f.ticks.get(); f.loop.step(); assertEquals(before + 1, f.ticks.get());
        assertEquals(0, f.pacing.capture().remainder(), "Canonical stepping spends no presentation fraction");
        var fresh = fixture(provider, 25);
        long samples = ((NativeSpecialStagePacing) provider).pacingState().acceptedSampleOrdinal();
        fresh.loop.stepPresentationFrame(); fresh.loop.stepPresentationFrame(); fresh.loop.stepPresentationFrame();
        assertEquals(0, fresh.ticks.get());
        assertEquals(samples, ((NativeSpecialStagePacing) provider).pacingState().acceptedSampleOrdinal());
        fresh.loop.stepPresentationFrame(); assertEquals(1, fresh.ticks.get());
    }

    private static Fixture fixture(SpecialStageProvider provider, int speed) throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        config().clearSessionOverrides();
        GraphicsManager.getInstance().resetState(); GraphicsManager.getInstance().initHeadless();
        config().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        provider.reset(); provider.initializeStage(0); warm(provider);
        var ticks = new AtomicInteger(); var pacing = new GameplayMutatorPacing(() -> policy(speed));
        var world = countingWorld(pacing, ticks); var context = mock(GameplayModeContext.class);
        when(context.getWorldSession()).thenReturn(world);
        when(context.isGameplayRuntimeReady()).thenReturn(true);
        var input = new InputHandler(); var loop = new PumpLoop(input); var audio = mock(AudioManager.class);
        install(loop, context, audio); set(loop, "activeSpecialStageProvider", provider); set(loop, "camera", null);
        loop.setGameMode(GameMode.SPECIAL_STAGE);
        var nativeInput = (GameLoopSpecialStageInput) get(loop, "specialStagePacingInput");
        loop.body = () -> NativeSpecialStageFrame.step(provider, world, () -> {
            nativeInput.apply(provider, input, world); provider.update();
        });
        return new Fixture(loop, provider, input, pacing, ticks, audio);
    }
    private static void warm(SpecialStageProvider provider) {
        var port = (NativeSpecialStagePacing) provider;
        for (int i = 0; i < 1000 && !port.pacingState().interactive(); i++) {
            provider.handleInput(0, 0); provider.handlePlayer2Input(0, 0); provider.update();
        }
        assertTrue(port.pacingState().interactive());
    }
    private static WorldSession countingWorld(GameplayMutatorPacing pacing, AtomicInteger ticks) {
        var module = mock(GameModule.class); when(module.getIdentifier()).thenReturn("test:native-stage-pacing");
        var state = new WorldSessionPolicyState() {
            public <T> T getService(Class<T> type) { return type == GameplayMutatorPacing.class ? type.cast(pacing) : null; }
            public RewindSnapshottable<?> rewindAdapter() { return pacing; }
            public void beforeSpecialStageForwardTick() { ticks.incrementAndGet(); }
            public void beforeAssembly(LevelLoadCause cause) { }
            public void bindRoster(SpriteManager sprites) { }
            public void failedAssembly(LevelLoadCause cause) { }
            public void closeScreens() { }
            public void retire() { pacing.reset(); }
        };
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> state);
        return new WorldSession(module);
    }
    private static GameplayMutatorPolicy policy(int speed) { return new GameplayMutatorPolicy(100, 0, 100, 0xC00, speed, true); }
    private static BonusStageState savedState() { return new BonusStageState(0, 0, 25, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 12, (byte) 13, 0, 0L); }
    private static SonicConfigurationService config() { return SonicConfigurationService.getInstance(); }
    private static void install(PumpLoop loop, GameplayModeContext context, AudioManager audio) throws Exception {
        set(loop, "gameplayMode", context); set(loop, "audioManager", audio); set(loop, "fadeManager", mock(FadeManager.class));
        set(loop, "outerFramePresentation", new OuterFramePresentation(audio)); set(loop, "titleCardProvider", null);
    }
    private static Object get(GameLoop loop, String name) throws Exception { Field f = GameLoop.class.getDeclaredField(name); f.setAccessible(true); return f.get(loop); }
    private static void set(GameLoop loop, String name, Object value) throws Exception { Field f = GameLoop.class.getDeclaredField(name); f.setAccessible(true); f.set(loop, value); }
    private static final class PumpLoop extends GameLoop {
        private final InputHandler input; private Runnable body = () -> { }; private Runnable afterTick = () -> { };
        PumpLoop(InputHandler input) { super(input); this.input = input; }
        @Override public void step() {
            input.refreshLogicalSnapshot(); if (!isPaused()) body.run(); input.update(); afterTick.run();
        }
    }
    private record Fixture(PumpLoop loop, SpecialStageProvider provider, InputHandler input,
                           GameplayMutatorPacing pacing, AtomicInteger ticks, AudioManager audio) { }
}
