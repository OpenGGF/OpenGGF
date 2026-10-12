package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.tests.MutatorPhysicsWorld;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated
class TestLiveRewindPacingFrames {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void internalFramesRemainAlignedThroughWrapGrowthPruningBranchRetainAndReset() {
        var f = fixture(); var source = new LiveRewindInputSource();
        var expected = new HashMap<Integer, LiveRewindPacingFrame>();
        assertNull(source.pacingFrame(0), "Synthetic frame zero has no admitted running-owner input");
        for (int frame = 1; frame <= 5000; frame++) {
            f.pacing.stepsForPresentation();
            var metadata = LiveRewindPacingFrame.capture(GameMode.LEVEL, f.context, null, f.input);
            assertNotNull(metadata); expected.put(frame, metadata);
            source.appendFrame(f.input, SonicConfigurationService.getInstance(), metadata);
            source.discardBefore(Math.max(0, frame - (frame < 1500 ? 90 : 700)));
            assertSame(metadata, source.pacingFrame(frame));
            assertSame(expected.get(source.earliestFrame()), source.pacingFrame(source.earliestFrame()));
        }
        for (int frame = source.earliestFrame(); frame <= 5000; frame++)
            assertSame(expected.get(frame), source.pacingFrame(frame));
        source.discardAfter(4800); assertNull(source.pacingFrame(4801));
        var branch = LiveRewindPacingFrame.capture(GameMode.LEVEL, f.context, null, f.input);
        source.appendFrame(f.input, SonicConfigurationService.getInstance(), branch);
        assertSame(branch, source.pacingFrame(4801));
        source.retainOnlyFrame(4801); assertSame(branch, source.pacingFrame(4801));
        assertNull(source.pacingFrame(4800));
        source.appendFrame(f.input, SonicConfigurationService.getInstance());
        assertNull(source.pacingFrame(4802), "Unannotated inputs keep the stock recorded-input fallback");
        source.resetToFrameZero(); assertNull(source.pacingFrame(0));
        source.retainOnlyFrame(9); assertNull(source.pacingFrame(9));
    }

    @Test void admittedReleasedEdgesAndPostTickFractionPreserveRecordedDriverModifiers() {
        var f = fixture(); f.pacing.stepsForPresentation();
        var admitted = PlayerInputState.of(0, 0, 0, 2, false, false);
        var p2 = PlayerInputState.of(0, 0, 0, 4, false, true);
        f.input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(admitted, p2));
        var metadata = LiveRewindPacingFrame.capture(GameMode.LEVEL, f.context, null, f.input);
        var driver = LogicalInputSnapshot.neutral().withDebugInput(true, true, true, true, true);
        var replay = metadata.admitted(driver, f.context, null);
        assertEquals(admitted, replay.player1()); assertEquals(p2, replay.player2());
        assertTrue(replay.debugModeTogglePressed()); assertTrue(replay.debugShiftDown());
        assertTrue(replay.debugControlDown()); assertTrue(replay.debugAltDown()); assertTrue(replay.debugSuperDown());
        assertEquals(driver.menuAccept(), replay.menuAccept());
        f.pacing.reset(); metadata.restoreAfterTick(f.context, null);
        assertEquals(metadata.afterTick(), f.pacing.capture());
        assertEquals(75, f.pacing.capture().remainder());
    }

    @Test void otherWorldRetiredContextLevelReplacementAndNewNativeEntryRejectMetadata() {
        var f = fixture(); f.pacing.stepsForPresentation();
        var provider = new Sonic2SpecialStageProvider();
        var metadata = LiveRewindPacingFrame.capture(GameMode.SPECIAL_STAGE, f.context, provider, f.input);
        assertNotNull(metadata); var driver = LogicalInputSnapshot.neutral();
        var other = fixture();
        assertSame(driver, metadata.admitted(driver, other.context, provider));
        f.pacing.reset(); metadata.restoreAfterTick(other.context, provider);
        assertEquals(0, f.pacing.capture().remainder());
        when(f.context.isGameplayRuntimeReady()).thenReturn(false);
        assertSame(driver, metadata.admitted(driver, f.context, provider));
        when(f.context.isGameplayRuntimeReady()).thenReturn(true);
        when(f.level.getCurrentLevel()).thenReturn(mock(Level.class));
        assertSame(driver, metadata.admitted(driver, f.context, provider));
        when(f.level.getCurrentLevel()).thenReturn(null);
        provider.pacingOwner().beginEntry();
        assertSame(driver, metadata.admitted(driver, f.context, provider));
        metadata.restoreAfterTick(f.context, provider);
        assertEquals(0, f.pacing.capture().remainder());
    }

    private static Fixture fixture() {
        var world = MutatorPhysicsWorld.create(() -> new GameplayMutatorPolicy(100, 0, 100, 0xC00, 75, false));
        var context = mock(GameplayModeContext.class); var level = mock(LevelManager.class);
        when(context.getWorldSession()).thenReturn(world); when(context.isGameplayRuntimeReady()).thenReturn(true);
        when(context.getLevelManager()).thenReturn(level);
        return new Fixture(context, level, new InputHandler(),
                WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class));
    }
    private record Fixture(GameplayModeContext context, LevelManager level, InputHandler input,
                           GameplayMutatorPacing pacing) { }
}
