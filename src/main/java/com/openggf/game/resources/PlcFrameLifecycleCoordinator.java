package com.openggf.game.resources;

import com.openggf.game.GameModule;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Assigns exactly one semantic PLC owner to each represented VBlank.
 */
public final class PlcFrameLifecycleCoordinator implements NativeFadeLifecycle {
    private final Supplier<PlcLifecycleService> serviceSupplier;
    private NativeBlockingFadeImpl activeFade;
    private PlcLifecycleFrame activeFrame;

    public PlcFrameLifecycleCoordinator(GameModule module) {
        this(() -> module.getGameService(PlcLifecycleService.class));
    }

    public PlcFrameLifecycleCoordinator(PlcLifecycleService service) {
        this(() -> service);
    }

    private PlcFrameLifecycleCoordinator(Supplier<PlcLifecycleService> serviceSupplier) {
        this.serviceSupplier = Objects.requireNonNull(serviceSupplier, "serviceSupplier");
    }

    public PlcLifecycleFrame latchBeforeFadeUpdate() {
        if (activeFrame != null && !activeFrame.finished) {
            throw new IllegalStateException("previous PLC lifecycle frame is still active");
        }
        activeFrame = new PlcLifecycleFrame(serviceSupplier.get());
        if (activeFade != null && !activeFade.closed) {
            activeFrame.claim(PlcLifecyclePhase.PALETTE_FADE);
        }
        return activeFrame;
    }

    /**
     * Runs one represented logical iteration through the shared live/headless
     * lifecycle. Fade advancement deliberately precedes mode work so a
     * completion callback cannot transfer the already-latched VBlank token to
     * the incoming mode.
     */
    public <T> T runLogicalIteration(
            Runnable fadeUpdate, Function<PlcLifecycleFrame, T> iteration) {
        Objects.requireNonNull(fadeUpdate, "fadeUpdate");
        Objects.requireNonNull(iteration, "iteration");
        PlcLifecycleFrame frame = latchBeforeFadeUpdate();
        Throwable primaryFailure = null;
        try {
            fadeUpdate.run();
            if (frame.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE)) {
                frame.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE);
            }
            return iteration.apply(frame);
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                frame.finish();
            } catch (RuntimeException | Error validationFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(validationFailure);
                } else {
                    throw validationFailure;
                }
            }
        }
    }

    @Override
    public NativeBlockingFade beginNativeBlockingFade() {
        if (activeFade != null && !activeFade.closed) {
            throw new IllegalStateException("a native blocking fade is already active");
        }
        activeFade = new NativeBlockingFadeImpl();
        return activeFade;
    }

    public void reset() {
        activeFrame = null;
        if (activeFade != null) {
            activeFade.closed = true;
            activeFade = null;
        }
    }

    public final class PlcLifecycleFrame {
        private final PlcLifecycleService service;
        private PlcLifecyclePhase owner;
        private boolean prepared;
        private boolean finished;

        private PlcLifecycleFrame(PlcLifecycleService service) {
            this.service = service;
        }

        public boolean claim(PlcLifecyclePhase phase) {
            requireOpen();
            Objects.requireNonNull(phase, "phase");
            if (owner != null) {
                return false;
            }
            owner = phase;
            if (service != null) {
                service.serviceVBlank(phase);
            }
            return true;
        }

        public void prepareAfterLoop(PlcLifecyclePhase phase) {
            requireOpen();
            if (owner != phase) {
                throw new IllegalStateException("PLC lifecycle frame is not owned by " + phase);
            }
            if (prepared) {
                throw new IllegalStateException("PLC lifecycle frame was already prepared");
            }
            if (service != null && service.hasPreparationBoundary(phase)) {
                service.prepareAfterLoop(phase);
                prepared = true;
            }
        }

        public boolean isOwnedBy(PlcLifecyclePhase phase) {
            return owner == phase;
        }

        public void finish() {
            requireOpen();
            if (service != null && owner != null
                    && service.hasPreparationBoundary(owner) && !prepared) {
                throw new IllegalStateException("missing PLC preparation for " + owner);
            }
            finished = true;
        }

        private void requireOpen() {
            if (finished) {
                throw new IllegalStateException("PLC lifecycle frame is finished");
            }
        }
    }

    private final class NativeBlockingFadeImpl implements NativeBlockingFade {
        private boolean closed;

        @Override
        public Runnable wrapCompletion(Runnable completion) {
            Runnable callback = completion != null ? completion : () -> { };
            return () -> {
                close();
                callback.run();
            };
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (activeFade == this) {
                    activeFade = null;
                }
            }
        }
    }
}
