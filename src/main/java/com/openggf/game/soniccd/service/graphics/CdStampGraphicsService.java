package com.openggf.game.soniccd.service.graphics;

import com.openggf.game.soniccd.device.CdStampGraphics;
import com.openggf.game.soniccd.device.CdStampRenderState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Single-operation semantic owner for the bounded 2M stamp source model. No CPU interpreter,
 * workers, frame loop, file access, MMIO facade, or timing-port integration is present. Only a
 * production submission and explicit caller clocks can advance readiness. The caller owns RAM,
 * ownership transitions, IRQ delivery and CPU polling; effects never write CPU state.
 *
 * Source: GPGX 051d430d3d1b54625f9900c8f152d7f232e06daf, core/cd_hw/gfx.c,
 * gfx_start (604-672), gfx_update (674-754); core/cd_hw/scd.c, scd_end_frame (1990-2008).
 * These are emulator source semantics with the timing limitations on cyclesPerLine(), not
 * physical timing certification. Source register changes during a draw are deliberately outside
 * the stable-request contract on CdStampOperationRequest.
 *
 * advance() reserves one complete native service batch and emits busy-clear/wake/IRQ effects
 * before render() calls the production pixel kernel. Finish that batch synchronously before
 * advancing, rebasing or handing RAM to another owner. No arbitrary callback can execute inside
 * the service. Busy clear alone does not make the final pixels observable; COMPLETE does.
 * External RAM writes must not race rendering. Snapshots include caller RAM explicitly.
 */
public final class CdStampGraphicsService {
    public enum WordRamMode { TWO_M, ONE_M }
    public enum WordRamOwner { MAIN, SUB }

    /** Semantic inputs from the future RAM owner, never permission obtained from a fixture. */
    public record WordRamAccess(WordRamMode mode, WordRamOwner owner) {
        public WordRamAccess {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(owner, "owner");
        }
    }

    public record CompletionSignals(boolean levelOneIrqEnabled, boolean pollingGraphicsRegister) { }
    public enum Phase { RUNNING, RENDER_PENDING, COMPLETE }

    /** Identity is local to this owner and is replaced on restore/reset/new submission. */
    public static final class Operation {
        private final long serial;
        private Operation(long serial) { this.serial = serial; }
        public long serial() { return serial; }
    }

    /** Identity also distinguishes batches of the same operation; tickets cannot be replayed. */
    public static final class RenderTicket {
        private RenderTicket() { }
    }

    /**
     * rendered retains actual kernel cursors. reportedRemainingLines is the source register after
     * reservation, so a completing RENDER_PENDING batch has busy=false and unrendered pixels.
     * Neither that readback nor a wake/IRQ request is a CPU instruction-completion timestamp.
     */
    public record Progress(Operation operation, CdStampOperationRequest request, CdStampClock graphicsClock,
                           CdStampRenderState rendered, int reportedRemainingLines, int pendingLines,
                           Phase phase, long serviceSequence) {
        public boolean busy() {
            return phase == Phase.RUNNING || (phase == Phase.RENDER_PENDING && reportedRemainingLines > 0);
        }
        public boolean finalPixelsRendered() { return phase == Phase.COMPLETE; }
        public int cyclesPerLine() { return request.cyclesPerLine(); }
    }

    public sealed interface Effect permits BusyCleared, PollWakeRequested, LevelOneInterruptRequested,
            FinalPixelsRendered { }
    public record BusyCleared(Progress beforePixels) implements Effect { }

    /**
     * Request a polling restart at least at this CD residual. The CPU owner preserves an already
     * ahead counter and implements its own stopped/poll state; this service never assigns it.
     * Source gfx_update (711-724) compares native unsigned residuals within the same CD epoch.
     */
    public record PollWakeRequested(CdStampClock minimumSubCpuClock) implements Effect { }
    public record LevelOneInterruptRequested() implements Effect { }
    public record FinalPixelsRendered(Progress afterPixels) implements Effect { }

    public record ServiceStep(Progress progress, List<Effect> effects, Optional<RenderTicket> renderTicket) {
        public ServiceStep {
            effects = List.copyOf(effects);
            Objects.requireNonNull(renderTicket, "renderTicket");
        }
    }

    public record RenderResult(Progress progress, List<Effect> effects) {
        public RenderResult { effects = List.copyOf(effects); }
    }

    /** Capture is immutable, owns a RAM copy, and can only be made from this production owner. */
    public static final class Snapshot {
        private final Progress progress;
        private final byte[] wordRam;
        private Snapshot(Progress progress, byte[] wordRam) {
            this.progress = progress;
            this.wordRam = wordRam.clone();
        }
        public Progress progress() { return progress; }
        public byte[] wordRamCopy() { return wordRam.clone(); }
    }

    private CdStampClock clock;
    private Progress progress;
    private RenderTicket pendingTicket;
    private long serial;

    public CdStampGraphicsService(long cdEpoch) { this(0, cdEpoch); }

    public CdStampGraphicsService(long resetEpoch, long cdEpoch) {
        clock = new CdStampClock(resetEpoch, cdEpoch, 0);
    }

    public CdStampClock graphicsClock() { return clock; }

    /** Start a complete stable request. Submission can wait while MAIN retains 2M RAM. */
    public Operation submit(CdStampOperationRequest request, CdStampClock startClock, WordRamAccess access) {
        Objects.requireNonNull(request, "request");
        requireTwoM(access);
        requireEpoch(startClock);
        if (progress != null && progress.phase() != Phase.COMPLETE) {
            throw new IllegalStateException("Complete or reset the current stamp operation before submitting");
        }
        Operation operation = nextOperation();
        progress = new Progress(operation, request, startClock, request.initialRenderState(),
                request.heightLines(), 0, Phase.RUNNING, 0);
        clock = startClock;
        return operation;
    }

    public Progress readback(Operation operation) {
        requireCurrent(operation);
        return progress;
    }

    public Optional<RenderTicket> pendingRender(Operation operation) {
        requireCurrent(operation);
        return Optional.ofNullable(pendingTicket);
    }

    /**
     * Reserve all lines due at this source service boundary, or fail without changing state if
     * lineBudget (0..255) cannot cover the complete batch. It never truncates a batch to fit a host
     * budget. Request/budget, ownership and domain checks precede effects; render() separately
     * validates the complete RAM extent before writing pixels.
     *
     * gfx_update (679-705) interprets the uint32 counter difference as signed int: nonpositive
     * elapsed does no work; positive elapsed uses CEILING division and may advance ahead of the
     * supplied clock. MAIN-owned 2M RAM instead sets gfx.cycles to the supplied counter, with no
     * progress (749-753). Unlike the source's permissive inequality, 1M mode is always rejected.
     */
    public ServiceStep advance(Operation operation, CdStampClock serviceClock, WordRamAccess access,
                               CompletionSignals signals, int lineBudget) {
        requireCurrent(operation);
        requireTwoM(access);
        requireEpoch(serviceClock);
        Objects.requireNonNull(signals, "signals");
        requireBudget(lineBudget);
        requireNoPending();
        if (progress.phase() == Phase.COMPLETE) { return step(List.of()); }
        if (access.owner() == WordRamOwner.MAIN) {
            setClock(serviceClock);
            return step(List.of());
        }
        int elapsed = (int) (serviceClock.cycles() - clock.cycles());
        if (elapsed <= 0) { return step(List.of()); }
        int cost = progress.cyclesPerLine();
        int lines = (int) Math.min(progress.rendered().remainingLines(),
                (elapsed + (long) cost - 1) / cost);
        if (lines > lineBudget) {
            throw new IllegalArgumentException("Line budget cannot cover this complete service batch");
        }
        if (progress.serviceSequence() == Long.MAX_VALUE) {
            throw new IllegalStateException("Stamp service sequence exhausted");
        }
        int remaining = progress.rendered().remainingLines() - lines;
        CdStampClock nextClock = new CdStampClock(clock.resetEpoch(), clock.epoch(),
                (clock.cycles() + (long) lines * cost) & CdStampClock.UINT32_MASK);
        Progress next = new Progress(operation, progress.request(), nextClock, progress.rendered(),
                remaining, lines, Phase.RENDER_PENDING, progress.serviceSequence() + 1);
        // gfx_update (702-734): clear remaining/busy, then wake a register poll, then request IRQ1.
        // The entire completing batch is still unrendered here, not just its last pixel.
        List<Effect> effects = new ArrayList<>(3);
        if (remaining == 0) {
            effects.add(new BusyCleared(next));
            if (signals.pollingGraphicsRegister()) { effects.add(new PollWakeRequested(nextClock)); }
            if (signals.levelOneIrqEnabled()) { effects.add(new LevelOneInterruptRequested()); }
        }
        progress = next;
        clock = nextClock;
        pendingTicket = new RenderTicket();
        return step(effects);
    }

    /**
     * Render the reserved batch exactly once through CdStampGraphics. The kernel reads a complete
     * line vector before its pixels and preserves sequential later-read/earlier-write behavior.
     * RAM remains live: no pixel preparation, cache or copied trace table changes those reads.
     */
    public RenderResult render(RenderTicket ticket, byte[] wordRam, WordRamAccess access) {
        Objects.requireNonNull(ticket, "ticket");
        requireRam(wordRam);
        requireTwoM(access);
        if (access.owner() != WordRamOwner.SUB) {
            throw new IllegalStateException("The reserved render must finish before a 2M RAM handoff");
        }
        if (ticket != pendingTicket || progress == null || progress.phase() != Phase.RENDER_PENDING) {
            throw new IllegalStateException("Stale or already rendered stamp batch");
        }
        var rendered = CdStampGraphics.executeLines(wordRam, progress.rendered(), progress.pendingLines());
        Phase phase = progress.reportedRemainingLines() == 0 ? Phase.COMPLETE : Phase.RUNNING;
        progress = new Progress(progress.operation(), progress.request(), clock, rendered,
                progress.reportedRemainingLines(), 0, phase, progress.serviceSequence());
        pendingTicket = null;
        return new RenderResult(progress, phase == Phase.COMPLETE
                ? List.of(new FinalPixelsRendered(progress)) : List.of());
    }

    /** Explicit CD-domain subtraction, including while idle or complete; cursors never advance. */
    public void rebase(CdStampClock.Rebase rebase) {
        Objects.requireNonNull(rebase, "rebase");
        requireNoPending();
        if (rebase.resetEpoch() != clock.resetEpoch() || rebase.fromEpoch() != clock.epoch()) {
            throw new IllegalArgumentException("Rebase does not match the current CD reset/rebase domain");
        }
        setClock(new CdStampClock(clock.resetEpoch(), rebase.toEpoch(),
                (clock.cycles() - rebase.subtractedCycles()) & CdStampClock.UINT32_MASK));
    }

    /** An explicit semantic reset enters the next reset epoch; no RAM bytes are undone. */
    public void reset(long cdEpoch) {
        if (clock.resetEpoch() == Long.MAX_VALUE) { throw new IllegalStateException("CD reset epoch exhausted"); }
        reset(clock.resetEpoch() + 1, cdEpoch);
    }

    /** Cancel and reset in an explicitly supplied new reset domain, independent of frame rebases. */
    public void reset(long resetEpoch, long cdEpoch) {
        CdStampClock resetClock = new CdStampClock(resetEpoch, cdEpoch, 0);
        if (resetEpoch <= clock.resetEpoch()) {
            throw new IllegalArgumentException("Reset must enter a newer CD reset epoch");
        }
        progress = null;
        pendingTicket = null;
        clock = resetClock;
    }

    public Snapshot capture(Operation operation, byte[] wordRam) {
        requireCurrent(operation);
        requireRam(wordRam);
        return new Snapshot(progress, wordRam);
    }

    /**
     * Restore memory and progress together, replacing all external operation/batch identities.
     * Already applied wake/IRQ effects belong in the caller's snapshot at the same boundary;
     * restore does not emit them again, including for a final batch still awaiting rendering.
     */
    public Operation restore(Snapshot snapshot, byte[] wordRam) {
        Objects.requireNonNull(snapshot, "snapshot");
        requireRam(wordRam);
        Operation operation = nextOperation();
        Progress saved = snapshot.progress;
        Progress restored = new Progress(operation, saved.request(), saved.graphicsClock(), saved.rendered(),
                saved.reportedRemainingLines(), saved.pendingLines(), saved.phase(), saved.serviceSequence());
        System.arraycopy(snapshot.wordRam, 0, wordRam, 0, wordRam.length);
        progress = restored;
        clock = restored.graphicsClock();
        pendingTicket = saved.phase() == Phase.RENDER_PENDING ? new RenderTicket() : null;
        return operation;
    }

    private ServiceStep step(List<Effect> effects) {
        return new ServiceStep(progress, effects, Optional.ofNullable(pendingTicket));
    }

    private Operation nextOperation() {
        if (serial == Long.MAX_VALUE) { throw new IllegalStateException("Stamp operation identity exhausted"); }
        return new Operation(++serial);
    }

    private void setClock(CdStampClock nextClock) {
        if (progress != null) {
            progress = new Progress(progress.operation(), progress.request(), nextClock, progress.rendered(),
                    progress.reportedRemainingLines(), progress.pendingLines(), progress.phase(),
                    progress.serviceSequence());
        }
        clock = nextClock;
    }

    private void requireCurrent(Operation operation) {
        Objects.requireNonNull(operation, "operation");
        if (progress == null || operation != progress.operation()) {
            throw new IllegalStateException("Stale or foreign stamp operation");
        }
    }

    private void requireEpoch(CdStampClock candidate) {
        Objects.requireNonNull(candidate, "clock");
        if (candidate.resetEpoch() != clock.resetEpoch() || candidate.epoch() != clock.epoch()) {
            throw new IllegalArgumentException("Clock requires an explicit matching CD reset/rebase domain");
        }
    }

    private void requireNoPending() {
        if (pendingTicket != null) { throw new IllegalStateException("Render the reserved batch first"); }
    }

    private static void requireTwoM(WordRamAccess access) {
        Objects.requireNonNull(access, "access");
        if (access.mode() != WordRamMode.TWO_M) {
            throw new IllegalArgumentException("1M Word RAM stamp service is not implemented");
        }
    }

    private static void requireBudget(int lines) {
        if (lines < 0 || lines > 255) { throw new IllegalArgumentException("Line budget must be 0 through 255"); }
    }

    private static void requireRam(byte[] wordRam) {
        Objects.requireNonNull(wordRam, "wordRam");
        if (wordRam.length != CdStampGraphics.WORD_RAM_BYTES) {
            throw new IllegalArgumentException("Expected exactly 256 KiB of 2M Word RAM");
        }
    }
}
