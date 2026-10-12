package com.openggf.game.internal;

import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.control.PlayerInputState;
import com.openggf.game.SpecialStageInputMapper;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.mutators.GameplayMutatorPacing;

/** Restored world edges become native held samples; live lag never acknowledges an unlatched pulse. */
public final class NativeSpecialStageInput {
    private NativeSpecialStageInput() { }

    public static void publish(SpecialStageProvider provider, GameplayMutatorPacing pacing,
                               InputHandler input, boolean retainFresh) {
        var nativeOwner = NativeStagePacingOwners.special(provider);
        if (nativeOwner == null || pacing == null) return;
        if (retainFresh) pacing.retain(input.logical());
        var previous = nativeOwner.pacingState();
        if (!previous.player2Supported()) pacing.discardUnsupportedPlayer2();
        if (!pacing.hasPendingInput()) return;
        InputHandlerInternalAccess.publishNativeStagePlayers(input,
                sample(input.logical().player1(), pacing.pendingPlayer1(), previous.player1Held()),
                previous.player2Supported()
                        ? sample(input.logical().player2(), pacing.pendingPlayer2(), previous.player2Held())
                        : input.logical().player2());
    }

    static PlayerInputState sample(PlayerInputState fresh, PlayerInputState pending, int previousHeld) {
        int edges = (pending.pressedMask() & 0x0f)
                | InputActionMasks.toMegaDriveButtonBits(pending.actionPressedMask())
                | (pending.startPressed() ? 0x80 : 0);
        int physical = (fresh.heldMask() & 0x0f)
                | InputActionMasks.toMegaDriveButtonBits(fresh.actionHeldMask())
                | (fresh.startHeld() ? 0x80 : 0);
        // A release/repress between slow ticks needs one native release sample first.
        // Pending edges plus the snapshotted native previous-held byte own this phase.
        int released = edges & previousHeld;
        int held = (physical | edges) & ~released;
        int pressed = (held & ~previousHeld) | (fresh.pressedMask() & 0x0f)
                | InputActionMasks.toMegaDriveButtonBits(fresh.actionPressedMask());
        pressed &= ~released;
        return PlayerInputState.of(held & 0x0f, pressed & 0x0f,
                actions(held), actions(pressed), (held & 0x80) != 0, (pressed & 0x80) != 0);
    }

    public static void acknowledge(GameplayMutatorPacing pacing,
                                   NativeSpecialStagePacing.State before,
                                   NativeSpecialStagePacing.State after) {
        if (pacing != null && after.acceptedSampleOrdinal() != before.acceptedSampleOrdinal()) {
            pacing.acknowledgeNativeSamples(after.player1Held() & ~before.player1Held(),
                    after.player2Held() & ~before.player2Held());
        }
    }

    public static void dispatch(SpecialStageProvider provider, InputHandler input) {
        var mapped = SpecialStageInputMapper.map(input.logical());
        provider.handleInput(mapped.p1Held(), mapped.p1Pressed(), input.isShiftDown(), input.isControlDown());
        provider.handlePlayer2Input(mapped.p2Held(), mapped.p2Logical());
    }

    private static int actions(int nativeButtons) {
        return ((nativeButtons & 0x40) != 0 ? InputActionMasks.ACTION_A : 0)
                | ((nativeButtons & 0x10) != 0 ? InputActionMasks.ACTION_B : 0)
                | ((nativeButtons & 0x20) != 0 ? InputActionMasks.ACTION_C : 0);
    }
}
