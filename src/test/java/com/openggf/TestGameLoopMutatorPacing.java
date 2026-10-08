package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.LevelInputOverlay;
import java.util.concurrent.atomic.AtomicBoolean;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.MutatorPhysicsWorld;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;

@Isolated
class TestGameLoopMutatorPacing {
    @AfterEach void clear() { SessionManager.clear(); SonicConfigurationService.getInstance().clearSessionOverrides(); }

    @ParameterizedTest @ValueSource(strings = {"s1", "s2", "s3k"})
    void quarterRateSkipsCanonicalBodyAndDeliversReleasedTapExactlyOnce(String game) throws Exception {
        Fixture f = fixture(game, 25, true);
        int jump = SonicConfigurationService.getInstance().getInt(SonicConfiguration.P1_A);
        f.input.handleKeyEvent(jump, GLFW_PRESS);
        f.loop.stepPresentationFrame();
        assertEquals(0, f.loop.admitted.size());
        assertTrue(f.pacing.hasPendingInput());
        f.input.handleKeyEvent(jump, GLFW_RELEASE);
        f.loop.stepPresentationFrame(); f.loop.stepPresentationFrame();
        assertEquals(0, f.loop.admitted.size(), "three host presentations enter no simulation/PLC/VInt body");
        f.loop.stepPresentationFrame();
        assertEquals(1, f.loop.admitted.size());
        assertEquals(0, f.loop.admitted.getFirst().player1().actionHeldMask());
        assertNotEquals(0, f.loop.admitted.getFirst().player1().actionPressedMask());
        assertFalse(f.loop.admitted.getFirst().menuAccept(), "retained gameplay press never repeats a host menu edge");
        for (int i = 0; i < 4; i++) f.loop.stepPresentationFrame();
        assertEquals(0, f.loop.admitted.getLast().player1().actionPressedMask());
        verify(f.audio, atLeastOnce()).setForwardPlaybackRate(0.25);
    }

    @ParameterizedTest @ValueSource(strings = {"s1", "s2", "s3k"})
    void heldRewindBypassesQuarterBudgetAndReleaseResumesWithoutPriorPlayerEdges(String game) throws Exception {
        Fixture f = fixture(game, 25, true);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_KEY, GLFW_KEY_R);
        int jump = config.getInt(SonicConfiguration.P1_A);
        int rewind = config.getInt(SonicConfiguration.LIVE_REWIND_KEY);
        f.input.handleKeyEvent(jump, GLFW_PRESS);
        f.loop.stepPresentationFrame();
        assertEquals(25, f.pacing.capture().remainder());
        assertTrue(f.pacing.hasPendingInput());
        f.input.handleKeyEvent(jump, GLFW_RELEASE);
        f.input.handleKeyEvent(rewind, GLFW_PRESS);
        f.loop.stepPresentationFrame();
        assertEquals(1, f.loop.admitted.size(), "held rewind reaches the canonical entry immediately");
        assertEquals(25, f.pacing.capture().remainder(), "rewind dispatch does not spend fractional forward time");
        assertFalse(f.pacing.hasPendingInput());
        assertEquals(0, f.loop.admitted.getLast().player1().actionPressedMask());
        verify(f.audio).setForwardPlaybackRate(1.0);
        f.loop.stepPresentationFrame();
        assertEquals(2, f.loop.admitted.size());
        assertEquals(25, f.pacing.capture().remainder());
        f.input.handleKeyEvent(rewind, GLFW_RELEASE);
        f.loop.stepPresentationFrame();
        assertEquals(2, f.loop.admitted.size(), "release resumes the remaining fractional budget");
        assertEquals(50, f.pacing.capture().remainder());
        assertEquals(0, f.pacing.pendingPlayer1().actionPressedMask());
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        f.input.handleKeyEvent(rewind, GLFW_PRESS);
        f.loop.stepPresentationFrame();
        assertEquals(2, f.loop.admitted.size(), "a disabled rewind key leaves normal fractional pacing active");
        assertEquals(75, f.pacing.capture().remainder());
        f.input.handleKeyEvent(rewind, GLFW_RELEASE);
        f.loop.stepPresentationFrame();
        assertEquals(3, f.loop.admitted.size());
        assertEquals(0, f.pacing.capture().remainder());
        assertEquals(0, f.loop.admitted.getLast().player1().actionPressedMask());
    }

    @Test void pauseOnSkippedPresentationConsumesHostEdgeAndDoesNotAdvanceRemainderAgain() throws Exception {
        Fixture f = fixture("s2", 25, true);
        int pause = SonicConfigurationService.getInstance().getInt(SonicConfiguration.PAUSE_KEY);
        f.input.handleKeyEvent(pause, GLFW_PRESS);
        f.loop.stepPresentationFrame();
        assertTrue(f.loop.isPaused());
        assertEquals(0, f.loop.admitted.size());
        assertEquals(25, f.pacing.capture().remainder());
        assertFalse(f.pacing.hasPendingInput());
        verify(f.audio).pause();
        verify(f.audio).setForwardPlaybackRate(1.0);
        f.input.handleKeyEvent(pause, GLFW_RELEASE);
        f.loop.stepPresentationFrame();
        assertEquals(25, f.pacing.capture().remainder(), "paused presentation does not consume fractional game time");
    }

    @Test void livePolicyEditAndRestoreKeepFractionAndEdgesWhileModeFadeAndExternalInputStopThePump() throws Exception {
        Fixture f = fixture("s3k", 25, false);
        int jump = SonicConfigurationService.getInstance().getInt(SonicConfiguration.P1_A);
        f.input.handleKeyEvent(jump, GLFW_PRESS);
        f.loop.stepPresentationFrame();
        var snapshot = f.pacing.capture();
        f.input.handleKeyEvent(jump, GLFW_RELEASE);
        f.policy.set(policy(400, false));
        f.loop.stopAfterOne = true;
        f.loop.stepPresentationFrame();
        assertEquals(1, f.loop.admitted.size(), "a mode boundary stops even a four-step presentation");
        assertFalse(f.pacing.hasPendingInput());
        f.loop.stopAfterOne = false; f.loop.setGameMode(GameMode.LEVEL);
        f.pacing.restore(snapshot); f.policy.set(policy(75, false));
        f.loop.stepPresentationFrame();
        assertEquals(2, f.loop.admitted.size());
        assertNotEquals(0, f.loop.admitted.getLast().player1().actionPressedMask());
        assertEquals(0, f.pacing.capture().remainder());
        f.pacing.restore(snapshot); when(f.fade.isActive()).thenReturn(true);
        f.policy.set(policy(400, true)); f.loop.stepPresentationFrame();
        assertEquals(3, f.loop.admitted.size());
        assertEquals(0, f.loop.admitted.getLast().player1().actionPressedMask(), "fade discards pre-fade player edges");
        assertFalse(f.pacing.hasPendingInput());
        when(f.fade.isActive()).thenReturn(false);
        f.input.setLogicalOverride(LogicalInputSnapshot.neutral());
        f.pacing.restore(snapshot);
        int before = f.loop.admitted.size();
        f.loop.stepPresentationFrame();
        assertEquals(before + 1, f.loop.admitted.size(), "explicit tool input admits exactly one canonical tick");
        assertEquals(LogicalInputSnapshot.neutral(), f.loop.admitted.getLast(), "externally supplied input stays exact");
        assertEquals(25, f.pacing.capture().remainder(), "canonical tools/trace do not spend the presentation budget");
        verify(f.audio, never()).setForwardPlaybackRate(0.75);
    }

    @Test void configurationOverlayOpensOnZeroStepAndConsumesPauseWithoutRetainingGameplayInput() throws Exception {
        var modal = new AtomicBoolean();
        var overlay = mock(LevelInputOverlay.class);
        when(overlay.pausesGameplay()).thenAnswer(i -> modal.get());
        when(overlay.handleInput(any())).thenAnswer(i -> { modal.set(true); return true; });
        Fixture f = fixture("s2", 25, true, overlay);
        f.input.handleKeyEvent(SonicConfigurationService.getInstance().getInt(SonicConfiguration.PAUSE_KEY), GLFW_PRESS);
        f.loop.stepPresentationFrame();
        verify(overlay).handleInput(f.input);
        assertEquals(0, f.loop.admitted.size());
        assertTrue(f.loop.isPaused());
        assertFalse(f.pacing.hasPendingInput());
        verify(f.audio).setForwardPlaybackRate(1.0);
    }

    private static GameplayMutatorPolicy policy(int speed, boolean audio) {
        return new GameplayMutatorPolicy(100, 0, 100, 0xC00, speed, audio);
    }

    private static Fixture fixture(String game, int speed, boolean follows) throws Exception {
        return fixture(game, speed, follows, null);
    }

    private static Fixture fixture(String game, int speed, boolean follows, LevelInputOverlay overlay) throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SonicConfigurationService.getInstance().clearSessionOverrides();
        GameModule nativeModule = switch (game) {
            case "s1" -> new Sonic1GameModule(); case "s3k" -> new Sonic3kGameModule(); default -> new Sonic2GameModule();
        };
        GameModule module = overlay == null ? nativeModule : new com.openggf.game.patch.DelegatingGameModule(nativeModule, "test:overlay-pacing") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == LevelInputOverlay.class ? type.cast(overlay) : super.getGameService(type);
            }
        };
        TestEnvironment.configureGameModuleFixture(module);
        var policy = new AtomicReference<>(policy(speed, follows));
        var world = MutatorPhysicsWorld.create(policy::get);
        var context = mock(GameplayModeContext.class);
        when(context.getWorldSession()).thenReturn(world);
        when(context.isGameplayRuntimeReady()).thenReturn(true);
        var pacing = WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class);
        InputHandler input = new InputHandler(InputBindingFactory.supplier(SonicConfigurationService.getInstance()));
        PumpLoop loop = new PumpLoop(input);
        var audio = mock(AudioManager.class); var fade = mock(FadeManager.class);
        set(loop, "gameplayMode", context); set(loop, "audioManager", audio); set(loop, "fadeManager", fade);
        loop.setGameMode(GameMode.LEVEL);
        return new Fixture(loop, input, pacing, policy, audio, fade);
    }

    private static void set(GameLoop loop, String name, Object value) throws Exception {
        Field field = GameLoop.class.getDeclaredField(name); field.setAccessible(true); field.set(loop, value);
    }

    private static final class PumpLoop extends GameLoop {
        final InputHandler input; final List<LogicalInputSnapshot> admitted = new ArrayList<>(); boolean stopAfterOne;
        PumpLoop(InputHandler input) { super(input); this.input = input; }
        @Override public void step() {
            input.refreshLogicalSnapshot();
            if (!isPaused()) admitted.add(input.logical());
            input.update();
            if (stopAfterOne) setGameMode(GameMode.TITLE_SCREEN);
        }
    }
    private record Fixture(PumpLoop loop, InputHandler input, GameplayMutatorPacing pacing,
                           AtomicReference<GameplayMutatorPolicy> policy, AudioManager audio, FadeManager fade) { }
}
