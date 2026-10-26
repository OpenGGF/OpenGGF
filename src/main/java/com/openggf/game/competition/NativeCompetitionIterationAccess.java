package com.openggf.game.competition;

import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Internal handoff of an admitted competition iteration to its native loop.
 * A screen can select a different loop without preparing that loop a second
 * time on return to GameLoop. The token never outlives the screen update.
 */
public final class NativeCompetitionIterationAccess {
    private static final ThreadLocal<Iteration> CURRENT = new ThreadLocal<>();

    private NativeCompetitionIterationAccess() { }

    public static void runAdmitted(PlcLifecycleFrame frame, Runnable screenUpdate) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(screenUpdate, "screenUpdate");
        if (CURRENT.get() != null) throw new IllegalStateException("Nested native competition iteration");
        var iteration = new Iteration(frame);
        CURRENT.set(iteration);
        try {
            screenUpdate.run();
            if (!iteration.loopRan) {
                if (frame.ownerPhase() != iteration.incomingOwner) {
                    throw new IllegalStateException("Native loop bypassed the iteration handoff");
                }
                prepareIdle(frame);
            } else if (frame.ownerPhase() == null) {
                // A canonical setup-only pass can return before claiming its
                // loop. It still closes this admitted iteration exactly once.
                prepareIdle(frame);
            }
        } finally {
            CURRENT.remove();
        }
    }

    /** Only valid during the admitted screen update, never during drawing. */
    public static PlcLifecycleFrame currentFrame() {
        var iteration = CURRENT.get();
        if (iteration == null) throw new IllegalStateException("No admitted native competition iteration");
        return iteration.frame;
    }

    /** The native executor owns claim, VBlank service and loop-tail preparation. */
    public static <T> T runLoop(PlcLifecycleFrame frame, Supplier<T> nativeLoop) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(nativeLoop, "nativeLoop");
        var iteration = CURRENT.get();
        if (iteration != null) {
            if (iteration.frame != frame) throw new IllegalStateException("Native loop used a foreign PLC token");
            if (iteration.loopRan) throw new IllegalStateException("Native competition loop already ran");
            if (frame.ownerPhase() != null) throw new IllegalStateException("Native loop cannot replace an existing PLC owner");
            iteration.loopRan = true;
        }
        return nativeLoop.get();
    }

    private static void prepareIdle(PlcLifecycleFrame frame) {
        // A blocking palette fade keeps its already prepared token. A screen
        // with no world work otherwise closes an ordinary competition wait.
        if (frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL)) {
            frame.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        }
    }

    private static final class Iteration {
        final PlcLifecycleFrame frame;
        final PlcLifecyclePhase incomingOwner;
        boolean loopRan;
        Iteration(PlcLifecycleFrame frame) {
            this.frame = frame;
            incomingOwner = frame.ownerPhase();
        }
    }
}
