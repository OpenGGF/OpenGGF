package com.openggf.game.internal;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestNativeSpecialStageControllerSamples {
    @Test void releasedTapIsNativeHeldAndPressedThenAcknowledgedOnlyOnAcceptedSample() {
        var pacing = new GameplayMutatorPacing(() -> GameplayMutatorPolicy.STOCK);
        var tap = PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_B, false, false);
        pacing.retain(LogicalInputSnapshot.ofPlayers(tap, PlayerInputState.neutral()));
        pacing.retain(LogicalInputSnapshot.neutral());
        var sample = NativeSpecialStageInput.sample(PlayerInputState.neutral(), pacing.pendingPlayer1(), 0);
        assertEquals(InputActionMasks.ACTION_B, sample.actionHeldMask());
        assertEquals(InputActionMasks.ACTION_B, sample.actionPressedMask());
        var owner = new NativeSpecialStagePacingOwner(); owner.beginEntry();
        var before = owner.state(true, 0, 0, false);
        NativeSpecialStageInput.acknowledge(pacing, before, before);
        assertTrue(pacing.hasPendingInput(), "No ReadJoypads acceptance during lag");
        owner.acceptedSample();
        NativeSpecialStageInput.acknowledge(pacing, before, owner.state(true, 0x10, 0, false));
        assertFalse(pacing.hasPendingInput());
    }

    @Test void releaseRepressNeedsAcceptedReleaseBeforeNewEdgeAndCapturesThatPhase() {
        var pacing = new GameplayMutatorPacing(() -> GameplayMutatorPolicy.STOCK);
        var tap = PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_B, false, false);
        pacing.retain(LogicalInputSnapshot.ofPlayers(tap, PlayerInputState.neutral()));
        var pending = pacing.capture();
        var release = NativeSpecialStageInput.sample(PlayerInputState.neutral(), pacing.pendingPlayer1(), 0x10);
        assertEquals(0, release.actionHeldMask()); assertEquals(0, release.actionPressedMask());
        var owner = new NativeSpecialStagePacingOwner(); owner.beginEntry(); owner.acceptedSample();
        var before = owner.state(true, 0x10, 0, false); owner.acceptedSample();
        NativeSpecialStageInput.acknowledge(pacing, before, owner.state(true, 0, 0, false));
        assertTrue(pacing.hasPendingInput());
        var press = NativeSpecialStageInput.sample(PlayerInputState.neutral(), pacing.pendingPlayer1(), 0);
        assertEquals(InputActionMasks.ACTION_B, press.actionHeldMask());
        assertEquals(InputActionMasks.ACTION_B, press.actionPressedMask());
        pacing.restore(pending);
        assertEquals(release, NativeSpecialStageInput.sample(PlayerInputState.neutral(), pacing.pendingPlayer1(), 0x10));
    }
}
