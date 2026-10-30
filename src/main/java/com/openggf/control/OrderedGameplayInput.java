package com.openggf.control;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.function.IntPredicate;

/**
 * Window-thread physical transitions waiting for a consuming logical pass. This is
 * host input, never captured/restored world state and never a scene-event drain.
 * A sample takes the longest ordered prefix changing each physical control once:
 * simultaneous controls share a sample, but a press and its release occupy distinct
 * samples. Only acknowledgment commits the prefix; rereads and aborts do not.
 */
final class OrderedGameplayInput {
    static final int KEY_COUNT = 512;
    private static final int PAD_CONTROLS = 8;
    private static final int CONTROL_COUNT = KEY_COUNT + 2 * PAD_CONTROLS;
    private static final int MAX_TRANSITIONS = 4096;
    private record Transition(long sequence, int control, boolean down) { }
    static final class Sample {
        final BitSet previous;
        final BitSet held;
        final long through;
        Sample(BitSet previous, BitSet held, long through) {
            this.previous = previous; this.held = held; this.through = through;
        }
        boolean down(int control) { return control >= 0 && held.get(control); }
        boolean pressed(int control) { return down(control) && !previous.get(control); }
        PlayerInputState player(int player) {
            int base = KEY_COUNT + player * PAD_CONTROLS;
            int heldDirections = 0, pressedDirections = 0, heldActions = 0, pressedActions = 0;
            for (int i = 0; i < 4; i++) {
                if (down(base + i)) heldDirections |= 1 << i;
                if (pressed(base + i)) pressedDirections |= 1 << i;
            }
            for (int i = 0; i < 3; i++) {
                if (down(base + 4 + i)) heldActions |= 1 << i;
                if (pressed(base + 4 + i)) pressedActions |= 1 << i;
            }
            return PlayerInputState.of(heldDirections, pressedDirections, heldActions, pressedActions,
                    down(base + 7), pressed(base + 7));
        }
    }
    private final ArrayDeque<Transition> pending = new ArrayDeque<>();
    private final BitSet observed = new BitSet(CONTROL_COUNT);
    private final BitSet admitted = new BitSet(CONTROL_COUNT);
    private long sequence;
    private boolean overflow;

    void observe(int control, boolean down) {
        if (observed.get(control) == down) return;
        observed.set(control, down);
        if (overflow) { admitted.set(control, down); return; }
        if (pending.size() == MAX_TRANSITIONS) {
            // Incomplete order cannot safely invent attacks. Rebaseline the whole
            // interval until its owner acknowledges, rather than retain half a tap.
            pending.clear(); admitted.clear(); admitted.or(observed); overflow = true;
        } else pending.addLast(new Transition(++sequence, control, down));
    }
    void observePlayers(PlayerInputState p1, PlayerInputState p2) {
        observePlayer(0, p1); observePlayer(1, p2);
    }
    private void observePlayer(int player, PlayerInputState state) {
        int base = KEY_COUNT + player * PAD_CONTROLS;
        for (int i = 0; i < 4; i++) observe(base + i, (state.heldMask() & (1 << i)) != 0);
        for (int i = 0; i < 3; i++) observe(base + 4 + i, (state.actionHeldMask() & (1 << i)) != 0);
        observe(base + 7, state.startHeld());
    }
    Sample sample() {
        BitSet held = (BitSet) admitted.clone();
        BitSet changed = new BitSet(CONTROL_COUNT);
        long through = 0;
        for (Transition event : pending) {
            if (changed.get(event.control)) break;
            changed.set(event.control); held.set(event.control, event.down); through = event.sequence;
        }
        return new Sample((BitSet) admitted.clone(), held, through);
    }
    void acknowledge(Sample sample) {
        admitted.clear(); admitted.or(sample.held);
        while (!pending.isEmpty() && pending.getFirst().sequence <= sample.through) pending.removeFirst();
        overflow = false;
    }
    void discard(IntPredicate controls, Sample sample) {
        pending.removeIf(event -> controls.test(event.control));
        for (int control = 0; control < CONTROL_COUNT; control++) {
            if (!controls.test(control)) continue;
            boolean down = observed.get(control);
            admitted.set(control, down);
            if (sample != null) { sample.previous.set(control, down); sample.held.set(control, down); }
        }
    }
    void discardAll(Sample sample) {
        discard(control -> true, sample);
        overflow = false;
    }
}
