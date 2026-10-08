package com.openggf;

import com.openggf.control.ExclusiveHeldInput;
import com.openggf.game.GameMode;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.GameplayModeContext;

import java.util.Objects;

/**
 * Engine-only process endpoint boundary. One lifetime lease owns live input and
 * exactly one production iteration per call, bypassing presentation pacing and
 * movie fast-forward pumps. The sequence is transport metadata, never a ROM clock.
 * Normal session registry checkpoints are supported between iterations. Automatic
 * live/scripted rewind is excluded because it captures inside the production body.
 */
public final class ExclusiveLiveGameDriver implements AutoCloseable {
    /** Session checkpoints include this engine-only endpoint state while its lease is alive. */
    public static final String REWIND_KEY = "challenge-exclusive-live-input";

    public enum Kind { GAMEPLAY, NATIVE_PAUSE, SETUP, TITLE, TRANSITION, LAG, MODE, HOST_PAUSED }

    public record Iteration(GameMode mode, Kind kind, boolean controllerPolled, long sequence) { }

    private final GameLoop loop;
    private final EngineContext services;
    private final ExclusiveHeldInput input;
    private final RewindRegistry rewindRegistry;
    private final Thread ownerThread = Thread.currentThread();
    private boolean closed;
    private boolean stepping;
    private long sequence;
    private Kind kind;
    private boolean controllerPolled;

    private ExclusiveLiveGameDriver(GameLoop loop, EngineContext services, ExclusiveHeldInput input) {
        this.loop = loop;
        this.services = services;
        this.input = input;
        this.rewindRegistry = loop.resolveGameplayModeContext().getRewindRegistry();
    }

    public static ExclusiveLiveGameDriver acquire(GameLoop loop) {
        Objects.requireNonNull(loop, "loop");
        synchronized (loop) {
            loop.validateExclusiveLiveAdmission();
            EngineContext services = loop.exclusiveLiveServices();
            var input = ExclusiveHeldInput.acquire(loop.getInputHandler());
            var driver = new ExclusiveLiveGameDriver(loop, services, input);
            boolean reserved = false;
            boolean registered = false;
            try {
                ExternalFrameOrInputOwnership.reserve(services, driver);
                reserved = true;
                driver.rewindRegistry.register(driver.new EndpointRewindAdapter());
                registered = true;
                loop.installExclusiveLiveDriver(driver);
                return driver;
            } catch (RuntimeException failure) {
                if (registered) {
                    loop.installExclusiveLiveDriver(null);
                    driver.rewindRegistry.deregister(REWIND_KEY);
                }
                if (reserved) ExternalFrameOrInputOwnership.release(services, driver);
                input.close();
                throw failure;
            }
        }
    }

    static void requireNoLiveOwner(EngineContext services) {
        if (ExternalFrameOrInputOwnership.liveOwnerActive(services)) {
            throw new IllegalStateException("Production stepping belongs to an exclusive live driver");
        }
    }

    static void validateGameplayContext(GameplayModeContext context) {
        if (context == null || !context.isGameplayRuntimeReady()
                || com.openggf.game.mode.ControlledFrameRuntime.controller(context) != null) {
            throw new IllegalStateException("Exclusive live drive requires a ready production gameplay session");
        }
        for (var work : com.openggf.game.timing.HardwareWorkKind.values()) {
            if (context.hardwareTiming().admissionPolicyFor(work)
                    != com.openggf.game.timing.HardwareReadinessAdmissionPolicy.LIVE) {
                throw new IllegalStateException("Exclusive live drive requires LIVE hardware readiness");
            }
        }
    }

    void runIteration(Runnable productionStep, Runnable afterStep, Runnable presenceTick) {
        if (loop.isPaused()) {
            hostPaused();
            return;
        }
        // One native iteration; preserve callback order and the production failure boundary.
        try {
            productionStep.run();
        } finally {
            afterStep.run();
            presenceTick.run();
        }
    }

    public Iteration step(int megaDriveHeldMask) {
        requireOwner();
        if (stepping) throw new IllegalStateException("A production iteration is already running");
        loop.validateExclusiveLiveAdmission();
        if (loop.resolveGameplayModeContext().getRewindRegistry() != rewindRegistry) {
            throw new IllegalStateException("Production iteration lease belongs to a previous gameplay session");
        }
        input.beginIteration(megaDriveHeldMask);
        stepping = true;
        kind = Kind.SETUP;
        controllerPolled = false;
        try {
            loop.runExclusiveLiveIteration(this);
            return new Iteration(loop.getCurrentGameMode(), kind, controllerPolled, ++sequence);
        } finally {
            input.finishIteration(controllerPolled);
            stepping = false;
        }
    }

    void classify(PlcLifecycleFrame frame) {
        // Vint_CtrlDMA (S2 s2.asm:998-1002) services only the DMA queue;
        // its explicit production declaration never implies Joypad_Read.
        if (frame.isOwnedBy(PlcLifecyclePhase.LAG) || frame.hasExplicitDmaQueueService()) {
            kind = Kind.LAG;
            controllerPolled = false;
        } else if (frame.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE)) {
            kind = Kind.TRANSITION;
            controllerPolled = false;
        } else {
            for (PlcLifecyclePhase phase : PlcLifecyclePhase.values()) {
                if (!frame.isOwnedBy(phase)) continue;
                kind = switch (phase) {
                    case NORMAL_PAUSE, SPECIAL_STAGE_PAUSE -> Kind.NATIVE_PAUSE;
                    case ORDINARY_LEVEL -> Kind.GAMEPLAY;
                    case LEVEL_TITLE_CARD -> Kind.TITLE;
                    default -> Kind.MODE;
                };
                controllerPolled = true;
                break;
            }
        }
    }

    void hostPaused() {
        kind = Kind.HOST_PAUSED;
        controllerPolled = false;
    }

    void retainUnpolledState() { input.retainUnpolledState(); }

    private record EndpointState(long sequence, ExclusiveHeldInput.PollState input) { }

    private final class EndpointRewindAdapter implements RewindSnapshottable<EndpointState> {
        @Override public String key() { return REWIND_KEY; }

        @Override public EndpointState capture() {
            requireBetweenIterations();
            return new EndpointState(sequence, input.capturePollState());
        }

        @Override public void restore(EndpointState snapshot) {
            requireBetweenIterations();
            Objects.requireNonNull(snapshot, "snapshot");
            input.restorePollState(snapshot.input());
            sequence = snapshot.sequence();
        }
    }

    private void requireBetweenIterations() {
        requireOwner();
        if (stepping) throw new IllegalStateException("Cannot checkpoint a running production iteration");
    }

    private void requireOwner() {
        if (closed || !ExternalFrameOrInputOwnership.ownedBy(services, this)) {
            throw new IllegalStateException("Production iteration lease is closed");
        }
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Production iteration lease belongs to another thread");
        }
    }

    @Override
    public void close() {
        if (closed) return;
        requireOwner();
        if (stepping) throw new IllegalStateException("Cannot close a running production iteration");
        loop.installExclusiveLiveDriver(null);
        rewindRegistry.deregister(REWIND_KEY);
        input.close();
        ExternalFrameOrInputOwnership.release(services, this);
        closed = true;
    }
}
