package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.internal.NativeSpecialStagePacing;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.game.rewind.RewindBoundary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Native input acceptance through the actual outer pump and live rewind owner. */
@Isolated
class TestNativeStagePacingRewind {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void heldRewindAndReleasePreserveRestoredLagPendingTapUntilNativeAcceptance() throws Exception {
        var rom = GameServices.rom().getRom();
        var policy = new FixedPolicyState();
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:stage-pacing-rewind") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) world -> policy) : super.getGameService(type);
            }
        };
        TestEnvironment.configureGameModuleFixture(module);
        RomManager.getInstance().setRom(rom);
        bootGraphics();
        var config = GameServices.configuration();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED, false);
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_KEY, GLFW_KEY_R);
        config.setConfigValue(SonicConfiguration.P1_B, GLFW_KEY_Z);
        var provider = (Sonic2SpecialStageProvider) module.getSpecialStageProvider();
        provider.initializeStage(0);
        var port = (NativeSpecialStagePacing) provider;
        warm(provider, port);
        var adapter = adapter(provider);
        Object beforeLag = null;
        for (int attempts = 0; attempts < 1000; attempts++) {
            var candidate = adapter.capture();
            long ordinal = port.pacingState().acceptedSampleOrdinal();
            provider.handleInput(0, 0); provider.handlePlayer2Input(0, 0); provider.update();
            if (ordinal == port.pacingState().acceptedSampleOrdinal()) { beforeLag = candidate; break; }
        }
        assertNotNull(beforeLag, "Exercise actual native lag before ReadJoypads, without a fitted frame index");
        adapter.restore(beforeLag);
        var input = new InputHandler(InputBindingFactory.supplier(config)); var loop = new GameLoop(input);
        var context = SessionManager.getCurrentGameplayMode();
        set(loop, "activeSpecialStageProvider", provider);
        context.registerSpecialStageAdapter(provider);
        loop.setGameMode(GameMode.SPECIAL_STAGE);
        var controller = context.getRewindController();
        assertNotNull(controller);
        controller.setGameplayCheckpointInterval(1);
        long ordinal = port.pacingState().acceptedSampleOrdinal();
        int jumpKey = config.getInt(SonicConfiguration.P1_B);
        input.handleKeyEvent(jumpKey, GLFW_PRESS); loop.stepPresentationFrame();
        assertEquals(2, policy.pacing.pendingPlayer1().actionPressedMask(), "Configured Engine binding sampled the host tap");
        input.handleKeyEvent(jumpKey, GLFW_RELEASE);
        for (int i = 0; i < 3; i++) loop.stepPresentationFrame();
        assertEquals(1, controller.currentFrame());
        assertEquals(ordinal, port.pacingState().acceptedSampleOrdinal(), "First native iteration is a real lag");
        assertTrue(policy.pacing.hasPendingInput());
        assertEquals(2, policy.pacing.pendingPlayer1().actionPressedMask());
        var pendingLag = policy.pacing.capture();
        var nativeLag = provider.getManager().captureComparisonState();
        int lagFrame = controller.currentFrame();
        for (int i = 0; i < 128 && policy.pacing.hasPendingInput(); i++) loop.stepPresentationFrame();
        assertFalse(policy.pacing.hasPendingInput());
        assertEquals(0x10, port.pacingState().player1Held() & 0x70);
        var accepted = provider.getManager().captureComparisonState();
        var pendingAccepted = policy.pacing.capture();
        int acceptedFrame = controller.currentFrame();
        for (int i = 0; i < 32 && !provider.getManager().getPlayers().getFirst().isJumping(); i++)
            loop.stepPresentationFrame();
        assertTrue(provider.getManager().getPlayers().getFirst().isJumping(), "Original native accepted tap causes jump");
        input.handleKeyEvent(GLFW_KEY_R, GLFW_PRESS);
        for (int i = 0; i < 128 && controller.currentFrame() > lagFrame; i++) loop.stepPresentationFrame();
        assertEquals(lagFrame, controller.currentFrame());
        assertEquals(nativeLag, provider.getManager().captureComparisonState());
        assertEquals(ordinal, port.pacingState().acceptedSampleOrdinal());
        assertEquals(pendingLag, policy.pacing.capture(), "Outer cleanup must preserve rewind-restored world input");
        input.handleKeyEvent(GLFW_KEY_R, GLFW_RELEASE);
        for (int i = 0; i < 128 && controller.currentFrame() < acceptedFrame; i++) loop.stepPresentationFrame();
        assertEquals(accepted, provider.getManager().captureComparisonState());
        assertEquals(pendingAccepted, policy.pacing.capture());
        for (int i = 0; i < 32 && !provider.getManager().getPlayers().getFirst().isJumping(); i++)
            loop.stepPresentationFrame();
        assertTrue(provider.getManager().getPlayers().getFirst().isJumping(), "Released rewind replays actual native jump");
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void blueSpheresStartupIsUnpacedButLaterPerfectBannerRemainsInteractive() throws Exception {
        bootGraphics(); var provider = new Sonic3kSpecialStageProvider(); provider.initializeStage(0);
        var port = (NativeSpecialStagePacing) provider;
        assertFalse(provider.getManager().getPlayer().isStarted());
        assertFalse(port.pacingState().interactive());
        warm(provider, port);
        assertTrue(provider.getManager().getPlayer().isStarted());
        provider.getManager().getBanner().triggerReEntry();
        assertTrue(provider.getManager().getBanner().isShowPerfect());
        assertTrue(provider.getManager().getBanner().isVisible());
        assertTrue(port.pacingState().interactive(), "PERFECT is an in-game banner, not the startup controller lock");
    }



    @Test @RequiresRom(SonicGame.SONIC_2)
    void stockSpeedDoesNotRetainTapAcrossNativeLag() throws Exception {
        var f = nativeFixture(100); var port = (NativeSpecialStagePacing) f.provider;
        var adapter = adapter(f.provider); Object beforeLag = null;
        for (int i = 0; i < 1000; i++) {
            var state = adapter.capture(); long ordinal = port.pacingState().acceptedSampleOrdinal();
            f.provider.handleInput(0, 0); f.provider.update();
            if (ordinal == port.pacingState().acceptedSampleOrdinal()) { beforeLag = state; break; }
        }
        assertNotNull(beforeLag); adapter.restore(beforeLag);
        long ordinal = port.pacingState().acceptedSampleOrdinal();
        f.provider.handleInput(0x10, 0x10); f.provider.update();
        assertEquals(ordinal, port.pacingState().acceptedSampleOrdinal());
        for (int i = 0; i < 32 && ordinal == port.pacingState().acceptedSampleOrdinal(); i++) {
            f.provider.handleInput(0, 0); f.provider.update();
        }
        var stock = f.provider.getManager().captureComparisonState();
        assertEquals(0, port.pacingState().player1Held());
        adapter.restore(beforeLag);
        f.input.handleKeyEvent(GLFW_KEY_Z, GLFW_PRESS); f.loop.stepPresentationFrame();
        assertEquals(2, f.input.logical().player1().actionHeldMask(), "Stock control really dispatches physical B");
        f.input.handleKeyEvent(GLFW_KEY_Z, GLFW_RELEASE);
        for (int i = 0; i < 32 && ordinal == port.pacingState().acceptedSampleOrdinal(); i++)
            f.loop.stepPresentationFrame();
        assertEquals(stock, f.provider.getManager().captureComparisonState(), "Stock speed keeps native ReadJoypads lag semantics");
        assertFalse(f.policy.pacing.hasPendingInput());
    }

    @ParameterizedTest @ValueSource(ints = {75, 150}) @RequiresRom(SonicGame.SONIC_2)
    void oldKeyframeResimulationRestoresNativePendingAndNonzeroFraction(int speed) throws Exception {
        var f = nativeFixture(speed);
        var controller = SessionManager.getCurrentGameplayMode().getRewindController();
        controller.setGameplayCheckpointInterval(10);
        f.input.handleKeyEvent(GLFW_KEY_Z, GLFW_PRESS);
        f.loop.stepPresentationFrame();
        f.input.handleKeyEvent(GLFW_KEY_Z, GLFW_RELEASE);
        for (int i = 0; i < 8 && controller.currentFrame() < 1; i++) f.loop.stepPresentationFrame();
        assertTrue(controller.currentFrame() >= 1);
        var target = f.policy.pacing.capture();
        int targetFrame = controller.currentFrame();
        assertNotEquals(0, target.remainder(), "Exercise an actual nonzero presentation fraction");
        var nativeTarget = f.provider.getManager().captureComparisonState();
        var sampleTarget = ((NativeSpecialStagePacing) f.provider).pacingState();
        for (int i = 0; i < 8; i++) f.loop.stepPresentationFrame();
        assertTrue(controller.currentFrame() > targetFrame);
        controller.seekTo(targetFrame);
        assertEquals(nativeTarget, f.provider.getManager().captureComparisonState());
        assertEquals(sampleTarget, ((NativeSpecialStagePacing) f.provider).pacingState());
        assertEquals(target, f.policy.pacing.capture(), "Canonical resimulation must restore the recorded pacing state");
        assertEquals(2, target.player1().actionPressedMask(), "The restored lag frame owns the actual released B edge");
        f.loop.stepPresentationFrame();
        assertEquals((target.remainder() + speed) % 100, f.policy.pacing.capture().remainder());
        for (int i = 0; i < 128 && (f.policy.pacing.hasPendingInput()
                || !f.provider.getManager().getPlayers().getFirst().isJumping()); i++) f.loop.stepPresentationFrame();
        assertFalse(f.policy.pacing.hasPendingInput());
        assertTrue(((NativeSpecialStagePacing) f.provider).pacingState().acceptedSampleOrdinal() > sampleTarget.acceptedSampleOrdinal());
        assertTrue(f.provider.getManager().getPlayers().getFirst().isJumping(), "Restored native pending input is accepted and jumps exactly through ReadJoypads");
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void olderMainLevelKeyframeReplaysActualReleasedTapJump() throws Exception {
        var policy = configureNativePolicy(25);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        for (int i = 0; i < 600 && fixture.sprite().getAir(); i++) fixture.stepIdleFrames(1);
        assertFalse(fixture.sprite().getAir(), "Reach actual native terrain before the tap");
        var config = GameServices.configuration();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setConfigValue(SonicConfiguration.P1_B, GLFW_KEY_Z);
        var input = new InputHandler(InputBindingFactory.supplier(config)); var loop = new GameLoop(input);
        var context = SessionManager.getCurrentGameplayMode();
        context.markRewindBoundary(RewindBoundary.LEVEL_LOAD);
        var controller = context.getRewindController(); assertNotNull(controller);
        controller.setGameplayCheckpointInterval(10);
        input.handleKeyEvent(GLFW_KEY_Z, GLFW_PRESS); loop.stepPresentationFrame();
        assertEquals(2, policy.pacing.pendingPlayer1().actionPressedMask(), "Configured Engine binding retains the released tap");
        input.handleKeyEvent(GLFW_KEY_Z, GLFW_RELEASE);
        for (int i = 0; i < 3; i++) loop.stepPresentationFrame();
        assertEquals(1, controller.currentFrame());
        assertTrue(fixture.sprite().getAir(), "Released host tap must cause an actual native jump");
        short speed = fixture.sprite().getYSpeed(); int y = fixture.sprite().getCentreY();
        var pacing = policy.pacing.capture();
        for (int i = 0; i < 4; i++) loop.stepPresentationFrame();
        controller.seekTo(1);
        assertTrue(fixture.sprite().getAir(), "Internal history must retain admitted pressed edges beyond held-only BK2 rows");
        assertEquals(speed, fixture.sprite().getYSpeed()); assertEquals(y, fixture.sprite().getCentreY());
        assertEquals(pacing, policy.pacing.capture());
    }

    private static NativeFixture nativeFixture(int speed) throws Exception {
        var policy = configureNativePolicy(speed);
        bootGraphics(); var config = GameServices.configuration();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setConfigValue(SonicConfiguration.P1_B, GLFW_KEY_Z);
        var provider = (Sonic2SpecialStageProvider) GameServices.module().getSpecialStageProvider();
        provider.initializeStage(0); var port = (NativeSpecialStagePacing) provider; warm(provider, port);
        var input = new InputHandler(InputBindingFactory.supplier(config)); var loop = new GameLoop(input);
        set(loop, "activeSpecialStageProvider", provider);
        SessionManager.getCurrentGameplayMode().registerSpecialStageAdapter(provider);
        loop.setGameMode(GameMode.SPECIAL_STAGE);
        return new NativeFixture(loop, input, provider, policy);
    }
    private static FixedPolicyState configureNativePolicy(int speed) throws Exception {
        var rom = GameServices.rom().getRom(); var policy = new FixedPolicyState(speed);
        var module = new DelegatingGameModule(new Sonic2GameModule(), "test:stage-pacing-history") {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) world -> policy) : super.getGameService(type);
            }
        };
        TestEnvironment.configureGameModuleFixture(module); RomManager.getInstance().setRom(rom);
        return policy;
    }
    private record NativeFixture(GameLoop loop, InputHandler input, Sonic2SpecialStageProvider provider,
                                 FixedPolicyState policy) { }

    private static void warm(SpecialStageProvider provider, NativeSpecialStagePacing port) {
        for (int i = 0; i < 1000 && !port.pacingState().interactive(); i++) {
            provider.handleInput(0, 0); provider.handlePlayer2Input(0, 0); provider.update();
        }
        assertTrue(port.pacingState().interactive());
    }
    @SuppressWarnings("unchecked")
    private static RewindSnapshottable<Object> adapter(SpecialStageProvider provider) {
        return (RewindSnapshottable<Object>) provider.rewindAdapter().orElseThrow();
    }
    private static void bootGraphics() {
        GraphicsManager.getInstance().resetState(); GraphicsManager.getInstance().initHeadless();
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
    }
    private static void set(GameLoop loop, String name, Object value) throws Exception {
        Field field = GameLoop.class.getDeclaredField(name); field.setAccessible(true); field.set(loop, value);
    }
    private static final class FixedPolicyState implements WorldSessionPolicyState {
        private final GameplayMutatorPacing pacing;
        FixedPolicyState() { this(25); }
        FixedPolicyState(int speed) { pacing = new GameplayMutatorPacing(
                () -> new GameplayMutatorPolicy(100, 0, 100, 0xC00, speed, false)); }
        public <T> T getService(Class<T> type) { return type == GameplayMutatorPacing.class ? type.cast(pacing) : null; }
        public RewindSnapshottable<?> rewindAdapter() { return pacing; }
        public void beforeAssembly(LevelLoadCause cause) { }
        public void bindRoster(SpriteManager sprites) { }
        public void failedAssembly(LevelLoadCause cause) { }
        public void closeScreens() { }
        public void retire() { pacing.reset(); }
    }
}
