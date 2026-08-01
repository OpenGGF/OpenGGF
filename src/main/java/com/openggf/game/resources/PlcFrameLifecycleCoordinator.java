package com.openggf.game.resources;

import com.openggf.game.GameModule;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.DynamicArtDmaServiceModel;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Assigns exactly one semantic PLC owner to each represented VBlank.
 */
public final class PlcFrameLifecycleCoordinator implements NativeFadeLifecycle {
    private final Supplier<PlcLifecycleService> serviceSupplier;
    private final DynamicArtLifecycleService dynamicArtLifecycle;
    private final DynamicArtDmaServiceModel dynamicArtDmaService;
    private NativeBlockingFadeImpl activeFade;
    private PlcLifecycleFrame activeFrame;
    private boolean comparisonSegmentsExternallyManaged;
    private boolean representedIterationWithoutVblank;
    private boolean vblankOverrunCarry;

    public PlcFrameLifecycleCoordinator(GameModule module) {
        this(() -> module.getGameService(PlcLifecycleService.class), null,
                resolveDynamicArtDmaService(module));
    }

    public PlcFrameLifecycleCoordinator(
            GameModule module,
            DynamicArtLifecycleService dynamicArtLifecycle) {
        this(() -> module.getGameService(PlcLifecycleService.class),
                dynamicArtLifecycle, resolveDynamicArtDmaService(module));
    }

    public PlcFrameLifecycleCoordinator(
            GameModule module,
            DynamicArtLifecycleService dynamicArtLifecycle,
            DynamicArtDmaServiceModel dynamicArtDmaService) {
        this(() -> module.getGameService(PlcLifecycleService.class),
                dynamicArtLifecycle, dynamicArtDmaService);
    }

    public PlcFrameLifecycleCoordinator(PlcLifecycleService service) {
        this(() -> service, null, DynamicArtDmaServiceModel.EVERY_CLAIM);
    }

    public PlcFrameLifecycleCoordinator(
            PlcLifecycleService service,
            DynamicArtLifecycleService dynamicArtLifecycle) {
        this(() -> service, dynamicArtLifecycle,
                DynamicArtDmaServiceModel.EVERY_CLAIM);
    }

    public PlcFrameLifecycleCoordinator(
            PlcLifecycleService service,
            DynamicArtLifecycleService dynamicArtLifecycle,
            DynamicArtDmaServiceModel dynamicArtDmaService) {
        this(() -> service, dynamicArtLifecycle, dynamicArtDmaService);
    }

    private PlcFrameLifecycleCoordinator(
            Supplier<PlcLifecycleService> serviceSupplier,
            DynamicArtLifecycleService dynamicArtLifecycle,
            DynamicArtDmaServiceModel dynamicArtDmaService) {
        this.serviceSupplier = Objects.requireNonNull(serviceSupplier, "serviceSupplier");
        this.dynamicArtLifecycle = dynamicArtLifecycle;
        this.dynamicArtDmaService = Objects.requireNonNull(
                dynamicArtDmaService, "dynamicArtDmaService");
    }

    private static DynamicArtDmaServiceModel resolveDynamicArtDmaService(
            GameModule module) {
        GameRules rules = Objects.requireNonNull(module, "module").getRules();
        return rules != null
                ? rules.dynamicArtDmaService()
                : DynamicArtDmaServiceModel.EVERY_CLAIM;
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

    /**
     * Declares that the iteration about to run is represented without any
     * V-blank having elapsed for it. Live play never sets this: every live
     * iteration is closed by a real V-blank. It is a hardware-timing
     * classification only -- no gameplay value, queue readiness or transfer
     * identity crosses this call.
     *
     * <p>ROM basis: {@code Vint_runcount} is bumped once per V-blank at
     * {@code VintRet} (docs/s2disasm/s2.asm:507-508) regardless of which
     * handler ran, and {@code ProcessDMAQueue} (s2.asm:1770) is reached only
     * from the real per-mode V-int handlers (the {@code Vint_Level} call at
     * s2.asm:781, reached from s2.asm:698), never from {@code Vint_Lag}
     * (s2.asm:529-580) -- S1's {@code VBlank_Lag}
     * (docs/s1disasm/sonic.asm:709-730) has the same shape.
     *
     * <p>Crucially the real handler calls {@code ProcessDMAQueue} <em>before</em>
     * {@code VintRet} bumps the counter, so a sample taken with neither counter
     * advanced still sits after the queue was drained: this iteration's own
     * closure IS a dynamic-art publication boundary. What is still mid-flight is
     * the <em>gameplay</em> iteration, so only physics stays suppressed, and it
     * is the following iteration -- the one that overran its V-blank and
     * consumes two {@code Vint_runcount} ticks -- that publishes on a later
     * boundary instead of its own.
     */
    public void markRepresentedIterationWithoutVblank() {
        representedIterationWithoutVblank = true;
    }

    public void reset() {
        activeFrame = null;
        representedIterationWithoutVblank = false;
        vblankOverrunCarry = false;
        if (activeFade != null) {
            activeFade.closed = true;
            activeFade = null;
        }
    }

    /**
     * Run replay uses explicit structural segment boundaries. This switch does
     * not accept expected events or any gameplay value.
     */
    public void setComparisonSegmentsExternallyManaged(boolean externallyManaged) {
        comparisonSegmentsExternallyManaged = externallyManaged;
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
            if (dynamicArtLifecycle != null
                    && dynamicArtLifecycle.isRunActive()) {
                // A represented iteration whose V-blank had not yet bumped
                // Vint_runcount still ran a real per-mode handler, and that
                // handler calls ProcessDMAQueue (docs/s2disasm/s2.asm:781,
                // routine at s2.asm:1770) long before VintRet increments the
                // counter (s2.asm:507-508). So the DMA queue was serviced for
                // the row being closed even though the phase handed here had to
                // be LAG to keep gameplay suppressed. A genuine Vint_Lag row
                // (Vint_routine still 0, s2.asm:483-484) never reaches
                // ProcessDMAQueue and keeps the per-game phase policy's answer.
                if (dynamicArtDmaService.services(phase)
                        || representedIterationWithoutVblank) {
                    dynamicArtLifecycle.serviceProductionVBlank();
                }
                if (!comparisonSegmentsExternallyManaged
                        && !dynamicArtLifecycle.isComparisonSegmentOpen()) {
                    dynamicArtLifecycle.openComparisonSegment();
                }
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
            // A dynamic-art edge publishes on the V-blank that closes the
            // iteration which produced it. Two shapes reach no such boundary:
            //   * a lag V-blank -- V_Int branched to Vint_Lag because
            //     Vint_routine was still 0 (docs/s2disasm/s2.asm:483-484, 529;
            //     docs/s1disasm/sonic.asm:709 VBlank_Lag), and Vint_Lag never
            //     calls ProcessDMAQueue (only the real handlers do:
            //     s2.asm:781, 899, 1000, 1046, 1083, 1138); and
            //   * the iteration that overran its V-blank. Its predecessor was
            //     sampled mid-V-int -- after ProcessDMAQueue (s2.asm:781) but
            //     before VintRet bumped Vint_runcount (s2.asm:507-508) -- so
            //     that predecessor did reach a publication boundary and must
            //     not be carried, while this iteration is
            //     still mid-flight at the boundary and its queue-add
            //     (s2.asm:1705 "to be issued the next time ProcessDMAQueue is
            //     called") lands after it. The carry is a one-shot consumed by
            //     the very next closure, never a frame number or route.
            boolean withoutVblank = representedIterationWithoutVblank;
            representedIterationWithoutVblank = false;
            // A row sampled mid-V-int is never carried: its real handler already
            // ran ProcessDMAQueue. That holds however many such rows occur in a
            // row, so the guard is on the row's own shape, not on a one-shot.
            boolean carried = (vblankOverrunCarry
                    || owner == PlcLifecyclePhase.LAG) && !withoutVblank;
            vblankOverrunCarry = withoutVblank;
            if (dynamicArtLifecycle != null && owner != null
                    && dynamicArtLifecycle.isRunActive()) {
                dynamicArtLifecycle.finishProductionIteration(carried);
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
