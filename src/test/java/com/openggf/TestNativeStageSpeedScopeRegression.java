package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputHandler;
import com.openggf.game.*;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.MutatorPhysicsWorld;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Baseline regressions use only existing APIs: no proposed native SPI is needed to reproduce the scope gap. */
@Isolated @RequiresRom(SonicGame.SONIC_3K)
class TestNativeStageSpeedScopeRegression {
    @AfterEach void clear() { TestEnvironment.resetAll(); }

    @Test void quarterSpeedDoesNotExecuteNativeBlueSphereTicksOnFirstThreePresentations() throws Exception {
        GraphicsManager.getInstance().resetState(); GraphicsManager.getInstance().initHeadless();
        var provider = new Sonic3kSpecialStageProvider(); provider.initializeStage(0);
        // Follow native start/banner completion instead of fitting a frame number.
        for (int i = 0; i < 1000 && !provider.getManager().getPlayer().isStarted(); i++) {
            provider.handleInput(0, 0); provider.update();
        }
        assertTrue(provider.getManager().getPlayer().isStarted());
        var loop = loop(25); loop.body = () -> { provider.handleInput(0, 0); provider.update(); };
        set(loop, "activeSpecialStageProvider", provider); set(loop, "camera", null);
        loop.setGameMode(GameMode.SPECIAL_STAGE);
        int before = provider.getManager().getFrameCounter();
        loop.stepPresentationFrame(); loop.stepPresentationFrame(); loop.stepPresentationFrame();
        assertEquals(before, provider.getManager().getFrameCounter(), "Current LEVEL-only qualification executes three unwanted native ticks");
        loop.stepPresentationFrame(); assertEquals(before + 1, provider.getManager().getFrameCounter());
    }

    @Test void fourTimesSpeedExecutesFourNativeIntegratedSlotFrames() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_SLOT_MACHINE, 0).build();
        var context = SessionManager.getCurrentGameplayMode();
        var provider = spy((Sonic3kBonusStageCoordinator) GameServices.module().getBonusStageProvider());
        context.setActiveBonusStageProvider(provider);
        provider.onEnter(BonusStageType.SLOT_MACHINE,
                new BonusStageState(0, 0, 25, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 12, (byte) 13, 0, 0L));
        provider.onDeferredSetupComplete(); assertTrue(provider.updateDuringLevelFrame());
        if (GameServices.level().hasPendingInitialProcessSpritesPass())
            assertEquals(LevelFrameResult.SETUP_ONLY,
                    LevelFrameStep.admit(LevelFrameContext.from(context), GameServices.level(), false).result());
        var loop = loop(400); loop.body = () -> fixture.stepIdleFrames(1);
        var pumpContext = (GameplayModeContext) get(loop, "gameplayMode");
        when(pumpContext.getActiveBonusStageProvider()).thenReturn(provider);
        set(loop, "activeBonusStageProvider", provider); set(loop, "camera", GameServices.camera());
        loop.setGameMode(GameMode.BONUS_STAGE);
        int before = GameServices.level().getFrameCounter(); clearInvocations(provider);
        loop.stepPresentationFrame();
        assertEquals(4, GameServices.level().getFrameCounter() - before, "Current LEVEL-only qualification executes one native bonus frame");
        verify(provider, times(4)).onFrameUpdate();
    }

    private static PumpLoop loop(int speed) throws Exception {
        var world = MutatorPhysicsWorld.create(() -> new GameplayMutatorPolicy(100, 0, 100, 0xC00, speed, false));
        var context = mock(GameplayModeContext.class); when(context.getWorldSession()).thenReturn(world);
        when(context.isGameplayRuntimeReady()).thenReturn(true);
        var loop = new PumpLoop(new InputHandler()); set(loop, "gameplayMode", context);
        set(loop, "audioManager", mock(AudioManager.class)); set(loop, "fadeManager", mock(FadeManager.class));
        set(loop, "titleCardProvider", null); return loop;
    }
    private static Object get(GameLoop loop, String name) throws Exception { Field f = GameLoop.class.getDeclaredField(name); f.setAccessible(true); return f.get(loop); }
    private static void set(GameLoop loop, String name, Object value) throws Exception { Field f = GameLoop.class.getDeclaredField(name); f.setAccessible(true); f.set(loop, value); }
    private static final class PumpLoop extends GameLoop {
        final InputHandler input; Runnable body = () -> { };
        PumpLoop(InputHandler input) { super(input); this.input = input; }
        @Override public void step() { input.refreshLogicalSnapshot(); body.run(); input.update(); }
    }
}
