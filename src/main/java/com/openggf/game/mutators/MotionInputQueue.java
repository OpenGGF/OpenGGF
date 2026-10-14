package com.openggf.game.mutators;

import com.openggf.control.InputActionMasks;
import com.openggf.control.PlayerInputState;
import com.openggf.mods.mutators.InputControlPolicy;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayDeque;
import java.util.List;

/** Native-tick input admission. Its complete tail and landing edge belong to rewind. */
final class MotionInputQueue {
    private final ArrayDeque<PlayerInputState> pending = new ArrayDeque<>();
    private long activation;
    private int delay;
    private boolean wasAirborne;

    PlayerInputState admit(PlayerInputState input, InputControlPolicy policy, long id,
                           boolean eligible, boolean airborne) {
        boolean landed = wasAirborne && !airborne;
        wasAirborne = eligible && airborne;
        if (!eligible || policy == null) {
            clearDelay();
            return input;
        }
        if (policy.mode() != InputControlPolicy.Mode.DELAY) clearDelay();
        return switch (policy.mode()) {
            case DELAY -> {
                if (activation != id || delay != policy.delayTicks()) {
                    clearDelay(); activation = id; delay = policy.delayTicks();
                }
                pending.addLast(PlayerInputState.of(input.heldMask(), input.pressedMask(),
                        input.actionHeldMask(), input.actionPressedMask(), false, false));
                PlayerInputState admitted = pending.size() > delay ? pending.removeFirst() : PlayerInputState.neutral();
                yield PlayerInputState.of(admitted.heldMask(), admitted.pressedMask(),
                        admitted.actionHeldMask(), admitted.actionPressedMask(), input.startHeld(), input.startPressed());
            }
            case REVERSE_HORIZONTAL -> PlayerInputState.of(swap(input.heldMask()), swap(input.pressedMask()),
                    input.actionHeldMask(), input.actionPressedMask(), input.startHeld(), input.startPressed());
            case LANDING_JUMP -> landed && (input.heldMask() & AbstractPlayableSprite.INPUT_DOWN) == 0
                    ? PlayerInputState.of(input.heldMask(), input.pressedMask(),
                    input.actionHeldMask() | InputActionMasks.ACTION_A,
                    input.actionPressedMask() | InputActionMasks.ACTION_A, input.startHeld(), input.startPressed())
                    : input;
            // Gate the ordinary doJump owner; retain spindash/shield/ability buttons.
            case NO_FRESH_JUMP -> input;
        };
    }

    private static int swap(int mask) {
        int left = AbstractPlayableSprite.INPUT_LEFT, right = AbstractPlayableSprite.INPUT_RIGHT;
        return (mask & ~(left | right)) | ((mask & left) != 0 ? right : 0) | ((mask & right) != 0 ? left : 0);
    }
    private void clearDelay() { pending.clear(); activation = 0; delay = 0; }
    void reset() { clearDelay(); wasAirborne = false; }
    Snapshot capture() { return new Snapshot(activation, delay, wasAirborne, List.copyOf(pending)); }
    void restore(Snapshot snapshot) {
        activation = snapshot.activation(); delay = snapshot.delay(); wasAirborne = snapshot.wasAirborne();
        pending.clear(); pending.addAll(snapshot.pending());
    }
    record Snapshot(long activation, int delay, boolean wasAirborne, List<PlayerInputState> pending) {
        Snapshot { pending = List.copyOf(pending); }
    }
}
