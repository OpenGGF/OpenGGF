package com.openggf.control;

import java.util.Objects;

/**
 * Engine-only held-pad owner for a process endpoint. This is deliberately separate
 * from replay overrides: no device polling, raw shortcuts, or supplied press edges.
 * The caller commits the baseline only when a production loop polled controllers.
 */
public final class ExclusiveHeldInput implements AutoCloseable {
    public record PollState(int lastPolledHeld) {
        public PollState {
            if ((lastPolledHeld & ~0xff) != 0) {
                throw new IllegalArgumentException("Held input must be a Genesis controller byte");
            }
        }
    }

    private final InputHandler input;
    private final Thread ownerThread = Thread.currentThread();
    private int lastPolled;
    private int offered;
    private boolean iterationOpen;
    private boolean closed;

    private ExclusiveHeldInput(InputHandler input) {
        this.input = input;
    }

    public static ExclusiveHeldInput acquire(InputHandler input) {
        Objects.requireNonNull(input, "input");
        synchronized (input) {
            if (input.exclusiveHeldInput != null || input.hasLogicalOverride()) {
                throw new IllegalStateException("Input already has an external owner");
            }
            var lease = new ExclusiveHeldInput(input);
            input.clearKeyState();
            input.exclusiveHeldInput = lease;
            input.installExclusiveSnapshot(LogicalInputSnapshot.neutral());
            return lease;
        }
    }

    /** Offers an active-high Genesis controller byte, not gameplay state. */
    public void beginIteration(int held) {
        requireOwner();
        if ((held & ~0xff) != 0) {
            throw new IllegalArgumentException("Held input must be a Genesis controller byte");
        }
        if (iterationOpen) throw new IllegalStateException("Input iteration already open");
        offered = held;
        iterationOpen = true;
        input.installExclusiveSnapshot(snapshot(offered, lastPolled));
    }

    /** A lag iteration preserves the poll baseline; Pause_Loop commits it. */
    public void finishIteration(boolean controllerPolled) {
        requireOwner();
        if (!iterationOpen) throw new IllegalStateException("No input iteration open");
        if (controllerPolled) lastPolled = offered;
        iterationOpen = false;
    }

    /** Blocking fade/lag VBlank keeps the ROM's previous held byte as well as its baseline. */
    public void retainUnpolledState() {
        requireOwner();
        if (!iterationOpen) throw new IllegalStateException("No input iteration open");
        input.installExclusiveSnapshot(snapshot(lastPolled, lastPolled));
    }

    /** Captures only the last native poll, never an in-flight offered sample. */
    public PollState capturePollState() {
        requireBetweenIterations();
        return new PollState(lastPolled);
    }

    /** The next offer derives its edges from the restored native poll baseline. */
    public void restorePollState(PollState state) {
        requireBetweenIterations();
        lastPolled = Objects.requireNonNull(state, "state").lastPolledHeld();
        offered = lastPolled;
        input.installExclusiveSnapshot(snapshot(lastPolled, lastPolled));
    }

    private void requireBetweenIterations() {
        requireOwner();
        if (iterationOpen) throw new IllegalStateException("Cannot checkpoint an open input iteration");
    }

    private static LogicalInputSnapshot snapshot(int held, int previous) {
        int pressed = held & ~previous;
        PlayerInputState player = PlayerInputState.of(held & 0xf, pressed & 0xf,
                actions(held), actions(pressed), (held & 0x80) != 0, (pressed & 0x80) != 0);
        return LogicalInputSnapshot.ofPlayers(player, PlayerInputState.neutral());
    }

    private static int actions(int bits) {
        return ((bits & 0x40) != 0 ? InputActionMasks.ACTION_A : 0)
                | ((bits & 0x10) != 0 ? InputActionMasks.ACTION_B : 0)
                | ((bits & 0x20) != 0 ? InputActionMasks.ACTION_C : 0);
    }

    private void requireOwner() {
        if (closed || input.exclusiveHeldInput != this) {
            throw new IllegalStateException("Input lease is closed");
        }
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Input lease belongs to another thread");
        }
    }

    @Override
    public void close() {
        if (closed) return;
        requireOwner();
        input.exclusiveHeldInput = null;
        input.installExclusiveSnapshot(LogicalInputSnapshot.neutral());
        input.clearKeyState();
        closed = true;
    }
}
