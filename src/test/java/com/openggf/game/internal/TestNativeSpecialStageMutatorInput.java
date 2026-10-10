package com.openggf.game.internal;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageProvider;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.MutatorPhysicsWorld;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import com.openggf.tests.TestEnvironment;

import static org.junit.jupiter.api.Assertions.*;

/** Draft regressions: real native managers, ROM assets and rewind adapters; no trace hydration. */
@Isolated
class TestNativeSpecialStageMutatorInput {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }
    private static final PlayerInputState TAP = PlayerInputState.of(0, 0, 0,
            InputActionMasks.ACTION_B, false, false);

    @Test @RequiresRom(SonicGame.SONIC_2)
    void s2LagKeepsReleasedTapUntilReadJoypadsAndRewindReplaysIt() throws Exception {
        bootGraphics();
        var provider = new Sonic2SpecialStageProvider();
        provider.initializeStage(0);
        var port = (NativeSpecialStagePacing) provider;
        waitForGameplay(provider, port);
        var nativeAdapter = adapter(provider);
        Object beforeLag = null;
        for (int attempts = 0; attempts < 1000; attempts++) {
            Object candidate = nativeAdapter.capture();
            long ordinal = port.pacingState().acceptedSampleOrdinal();
            provider.handleInput(0, 0); provider.handlePlayer2Input(0, 0); provider.update();
            if (ordinal == port.pacingState().acceptedSampleOrdinal()) {
                beforeLag = candidate; break;
            }
        }
        assertNotNull(beforeLag, "Actual live S2 lag must be exercised, not a mocked update");
        nativeAdapter.restore(beforeLag);
        var world = world(); var pacing = pacing(world);
        pacing.retain(LogicalInputSnapshot.ofPlayers(TAP, PlayerInputState.neutral()));
        pacing.retain(LogicalInputSnapshot.neutral());
        var pendingBefore = pacing.capture();
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        long before = port.pacingState().acceptedSampleOrdinal();
        replayNeutral(provider, world, input);
        assertEquals(before, port.pacingState().acceptedSampleOrdinal());
        assertTrue(pacing.hasPendingInput(), "Calling update during lag is not ReadJoypads acceptance");
        int iterations = runUntilConsumed(provider, world, input, pacing);
        assertEquals(0x10, port.pacingState().player1Held() & 0x70);
        var after = provider.getManager().captureComparisonState();
        var pendingAfter = pacing.capture();
        // The actual latch schedules the recurring native main pass; prove the P1 jump downstream.
        for (int i = 0; i < 8 && !provider.getManager().getPlayers().getFirst().isJumping(); i++)
            replayNeutral(provider, world, input);
        assertTrue(provider.getManager().getPlayers().getFirst().isJumping());
        nativeAdapter.restore(beforeLag); pacing.restore(pendingBefore);
        assertEquals(before, port.pacingState().acceptedSampleOrdinal(), "Native ordinal restores with adapter");
        replayNeutral(provider, world, input);
        for (int i = 0; i < iterations; i++) replayNeutral(provider, world, input);
        assertEquals(after, provider.getManager().captureComparisonState());
        assertEquals(pendingAfter, pacing.capture(), "Fraction and remaining edges reproduce after restore");
        for (int i = 0; i < 8 && !provider.getManager().getPlayers().getFirst().isJumping(); i++)
            replayNeutral(provider, world, input);
        assertTrue(provider.getManager().getPlayers().getFirst().isJumping(), "Restored pending tap replays the actual native jump");
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void s2Player2ReleasedDirectionBecomesOneSupportedManualControllerSample() throws Exception {
        bootGraphics(); var provider = new Sonic2SpecialStageProvider(); provider.initializeStage(0);
        var port = (NativeSpecialStagePacing) provider; waitForGameplay(provider, port);
        var world = world(); var pacing = pacing(world);
        var right = PlayerInputState.of(0, 8, 0, 0, false, false);
        pacing.retain(LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), right));
        pacing.retain(LogicalInputSnapshot.neutral());
        var input = new InputHandler(); input.setLogicalOverride(LogicalInputSnapshot.neutral());
        runUntilConsumed(provider, world, input, pacing);
        assertEquals(8, port.pacingState().player2Held() & 0x0f);
        for (int i = 0; i < 8 && provider.getManager().captureComparisonState().tailsControlCounter() == 0; i++)
            replayNeutral(provider, world, input);
        assertTrue(provider.getManager().captureComparisonState().tailsControlCounter() > 0,
                "Native recurring pass must enter the supported human-P2 branch");
        // S2's FixBugs=0 manual P2 branch carries held/logical direction and zero pressed.
        // Do not change that native branch to promise a P2 jump it does not implement.
        long ordinal = port.pacingState().acceptedSampleOrdinal();
        for (int i = 0; i < 32 && ordinal == port.pacingState().acceptedSampleOrdinal(); i++)
            replayNeutral(provider, world, input);
        assertEquals(0, port.pacingState().player2Held());
        assertFalse(pacing.hasPendingInput());
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void s3kReleasedP1AndP2JumpRestoreThroughNativeAdapterAndReplay() throws Exception {
        bootGraphics(); var provider = new Sonic3kSpecialStageProvider(); provider.initializeStage(0);
        var port = (NativeSpecialStagePacing) provider; waitForGameplay(provider, port);
        var world = world(); var pacing = pacing(world);
        pacing.retain(LogicalInputSnapshot.ofPlayers(TAP, TAP)); pacing.retain(LogicalInputSnapshot.neutral());
        var pending = pacing.capture(); var nativeAdapter = adapter(provider); var nativeBefore = nativeAdapter.capture();
        var input = new InputHandler(); input.setLogicalOverride(LogicalInputSnapshot.neutral());
        replayNeutral(provider, world, input);
        assertNotEquals(0, provider.getManager().getPlayer().getJumping());
        assertTrue(port.pacingState().player2Supported());
        assertNotEquals(0, provider.getManager().getTailsJumpHeight(), "P2 native held pulse reaches Tails jump physics");
        var after = provider.getManager().captureComparisonState(); var consumed = pacing.capture();
        assertFalse(pacing.hasPendingInput());
        nativeAdapter.restore(nativeBefore); pacing.restore(pending);
        replayNeutral(provider, world, input);
        assertEquals(after, provider.getManager().captureComparisonState());
        assertEquals(consumed, pacing.capture());
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void s1ReleasedJumpRestoresAndUnsupportedP2TapDoesNotLatchForever() throws Exception {
        bootGraphics(); var provider = new Sonic1SpecialStageProvider(); provider.initializeStage(0);
        var port = (NativeSpecialStagePacing) provider; waitForGameplay(provider, port);
        assertFalse(port.pacingState().player2Supported());
        provider.handleInput(0, 0); provider.update(); // Execute native one-shot Obj09 init before waiting for ground.
        for (int i = 0; i < 600 && provider.getManager().captureComparisonState().sonicAirborne(); i++) {
            provider.handleInput(0, 0); provider.update();
        }
        assertFalse(provider.getManager().captureComparisonState().sonicAirborne(), "Native ground contact, not an injected state");
        var world = world(); var pacing = pacing(world);
        pacing.retain(LogicalInputSnapshot.ofPlayers(TAP, TAP)); pacing.retain(LogicalInputSnapshot.neutral());
        var pending = pacing.capture(); var nativeAdapter = adapter(provider); var nativeBefore = nativeAdapter.capture();
        var input = new InputHandler(); replayNeutral(provider, world, input);
        assertTrue(provider.getManager().captureComparisonState().sonicAirborne());
        assertFalse(pacing.hasPendingInput(), "Unsupported P2 does not keep an otherwise consumed pulse pending");
        var after = provider.getManager().captureComparisonState(); var consumed = pacing.capture();
        nativeAdapter.restore(nativeBefore); pacing.restore(pending); replayNeutral(provider, world, input);
        assertEquals(after, provider.getManager().captureComparisonState()); assertEquals(consumed, pacing.capture());
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void nativeSoloS2DiscardsUnsupportedP2Pulse() throws Exception {
        bootGraphics(); GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var provider = new Sonic2SpecialStageProvider(); provider.initializeStage(0);
        var port = (NativeSpecialStagePacing) provider; waitForGameplay(provider, port);
        assertFalse(port.pacingState().player2Supported());
        var world = world(); var pacing = pacing(world);
        pacing.retain(LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), TAP));
        pacing.retain(LogicalInputSnapshot.neutral());
        replayNeutral(provider, world, new InputHandler());
        assertFalse(pacing.hasPendingInput());
    }

    private static void replayNeutral(SpecialStageProvider provider, WorldSession world, InputHandler input) {
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        NativeSpecialStageFrame.replay(provider, world, input);
    }

    private static int runUntilConsumed(SpecialStageProvider provider, WorldSession world,
                                        InputHandler input, GameplayMutatorPacing pacing) {
        int count = 0;
        while (pacing.hasPendingInput() && count < 32) { replayNeutral(provider, world, input); count++; }
        assertFalse(pacing.hasPendingInput(), "Native sample must accept the retained edge in bounded live cadence");
        return count;
    }
    private static void waitForGameplay(SpecialStageProvider provider, NativeSpecialStagePacing port) {
        for (int i = 0; i < 1000 && !port.pacingState().interactive(); i++) {
            provider.handleInput(0, 0); provider.handlePlayer2Input(0, 0); provider.update();
        }
        assertTrue(port.pacingState().interactive());
    }
    private static WorldSession world() {
        return MutatorPhysicsWorld.create(() -> new GameplayMutatorPolicy(100, 0, 100, 0xC00, 25, false));
    }
    private static GameplayMutatorPacing pacing(WorldSession world) {
        return WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class);
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
}
