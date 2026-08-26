package com.openggf.audio.synth;

import com.openggf.audio.smps.DacData;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class VirtualSynthesizer implements Synthesizer {
    private static final int YM_WRITE_TIMELINE_CAPACITY = 4_096;

    public enum ChipClockProfile {
        // Mega Drive master clocks: NTSC 53,693,175 Hz and PAL 53,203,424 Hz.
        // YM2612 = master/7; Z80 and PSG input use the regional /15 clock.
        NTSC(7_670_453.0, 3_579_545.0),
        PAL(53_203_424.0 / 7.0, 3_546_893.0);

        private final double ymClock;
        private final double psgClock;

        ChipClockProfile(double ymClock, double psgClock) {
            this.ymClock = ymClock;
            this.psgClock = psgClock;
        }
    }
    private final PsgChip psg;
    private final Ym2612Chip ym;
    private final YmWriteTimeline ymWriteTimeline;
    private final Object ymTimelineStateLock = new Object();
    private long ymTimelineGeneration;
    private double outputSampleRate = Ym2612Chip.getDefaultOutputRate();
    private boolean chipWriteObserverEnabled;

    // Scratch buffers for render() to avoid per-call allocations.
    private int[] scratchLeft = new int[0];
    private int[] scratchRight = new int[0];

    public VirtualSynthesizer() {
        this(Ym2612Chip.getDefaultOutputRate(), ChipWriteObserver.NONE);
    }

    public VirtualSynthesizer(double outputSampleRate) {
        this(outputSampleRate, ChipWriteObserver.NONE);
    }

    public VirtualSynthesizer(
            double outputSampleRate, ChipWriteObserver observer) {
        // Use the GPGX PSG core for better timing/pitch parity with Genesis hardware.
        this.psg = new PsgChip(outputSampleRate, PsgChip.ChipType.INTEGRATED);
        this.ym = new Ym2612Chip();
        this.ymWriteTimeline = new YmWriteTimeline(
                YM_WRITE_TIMELINE_CAPACITY);
        this.ym.setWriteTimeline(ymWriteTimeline);
        setChipWriteObserver(observer);
        setOutputSampleRate(outputSampleRate);
        // Match typical driver init: silence chips on startup to avoid power-on noise.
        silenceAll();
    }

    public void setOutputSampleRate(double outputSampleRate) {
        if (outputSampleRate <= 0.0) {
            return;
        }
        this.outputSampleRate = outputSampleRate;
        ym.setOutputSampleRate(outputSampleRate);
        psg.setSampleRate(outputSampleRate);
    }

    public double getOutputSampleRate() {
        return outputSampleRate;
    }

    public void setChipClockProfile(ChipClockProfile profile) {
        ym.setClockRates(profile.ymClock, profile.psgClock);
        psg.setInputClock(profile.psgClock);
    }

    public final double ymChipClockForTesting() {
        return ym.chipClockForTesting();
    }

    public final double psgInputClockForTesting() {
        return psg.inputClockForTesting();
    }

    final YmWriteTimeline ymWriteTimelineForTesting() {
        return ymWriteTimeline;
    }

    /** Named causes which invalidate already-published writes from older owners. */
    protected enum YmTimelineGenerationBarrier {
        HARD_RESET,
        SYNTH_REPLACEMENT,
        FULL_SILENCE
    }

    /** Returns the generation which must stamp newly published YM writes. */
    protected final long ymTimelineGeneration() {
        synchronized (ymTimelineStateLock) {
            return ymTimelineGeneration;
        }
    }

    /** Returns the internal YM frontier used to anchor source-timed writes. */
    protected final long renderedYmMasterCycle() {
        return ym.renderedMasterCycleFrontier();
    }

    /** Returns the latest due cycle already committed to the synth timeline. */
    protected final long lastPendingYmWriteDueCycle() {
        synchronized (ymTimelineStateLock) {
            return ymWriteTimeline.captureSnapshot().pending().stream()
                    .mapToLong(YmWriteTimeline.Entry::dueMasterCycle)
                    .max().orElse(0L);
        }
    }

    /** Returns the fixed publication capacity owned by this synth. */
    protected final int ymWriteTimelineCapacity() {
        synchronized (ymTimelineStateLock) {
            return ymWriteTimeline.captureSnapshot().capacity();
        }
    }

    /** Returns the number of writes already committed but not yet drained. */
    protected final int pendingYmWriteCount() {
        synchronized (ymTimelineStateLock) {
            return ymWriteTimeline.captureSnapshot().pending().size();
        }
    }

    /** Returns whether render still owns at least one committed YM write. */
    protected final boolean hasPendingYmWrites() {
        return pendingYmWriteCount() != 0;
    }

    /** Returns the first unused immutable source-write identity. */
    protected final long nextCommittedYmWriteOrdinal() {
        synchronized (ymTimelineStateLock) {
            return ymWriteTimeline.captureSnapshot().nextOrdinal();
        }
    }

    /**
     * Atomically verifies the current synth generation and publishes one
     * already-authorized source write journal. A journal built against an old
     * generation cannot cross a reset/replacement barrier unnoticed.
     */
    protected final void commitYmWriteJournal(
            List<YmWriteTimeline.Entry> journal) {
        Objects.requireNonNull(journal, "journal");
        synchronized (ymTimelineStateLock) {
            for (YmWriteTimeline.Entry entry : journal) {
                if (entry == null
                        || entry.driverGeneration()
                        != ymTimelineGeneration) {
                    throw new IllegalArgumentException(
                            "every YM write must use the current synth generation "
                                    + ymTimelineGeneration);
                }
            }
            ymWriteTimeline.commit(journal);
        }
    }

    /**
     * Invalidates older committed writes before a hardware ownership barrier.
     * The named cause keeps reset/replacement call sites explicit when driver
     * wiring is added.
     */
    protected final void crossYmTimelineGenerationBarrier(
            YmTimelineGenerationBarrier barrier) {
        Objects.requireNonNull(barrier, "barrier");
        synchronized (ymTimelineStateLock) {
            long nextGeneration = Math.incrementExact(
                    ymTimelineGeneration);
            ymTimelineGeneration = nextGeneration;
            ymWriteTimeline.discardBeforeGeneration(nextGeneration);
        }
    }

    /**
     * Installs one diagnostic observer at both resolved chip-write boundaries.
     * Passing {@code null} restores the disabled no-op observer.
     */
    public void setChipWriteObserver(ChipWriteObserver observer) {
        ym.setWriteObserver(observer);
        psg.setWriteObserver(observer);
        chipWriteObserverEnabled = observer != null
                && observer != ChipWriteObserver.NONE;
    }

    /** Returns whether chip writes can currently invoke user code. */
    protected final boolean hasChipWriteObserver() {
        return chipWriteObserverEnabled;
    }

    public Snapshot captureSynthSnapshot() {
        synchronized (ymTimelineStateLock) {
            return new Snapshot(
                    outputSampleRate,
                    ym.captureSnapshot(),
                    psg.captureSnapshot(),
                    ymWriteTimeline.captureSnapshot(),
                    ym.renderedMasterCycleFrontier(),
                    ymTimelineGeneration);
        }
    }

    public void restoreSynthSnapshot(Snapshot snapshot) {
        synchronized (ymTimelineStateLock) {
            YmWriteTimeline.Snapshot restoredTimeline =
                    validateSynthSnapshot(snapshot);

            outputSampleRate = snapshot.outputSampleRate();
            ym.restoreSnapshot(snapshot.ym());
            psg.restoreSnapshot(snapshot.psg());
            ymWriteTimeline.restoreSnapshot(restoredTimeline);
            ym.restoreRenderedMasterCycles(
                    snapshot.renderedYmMasterCycle());
            ymTimelineGeneration = snapshot.ymTimelineGeneration();
        }
    }

    private static YmWriteTimeline.Snapshot validateSynthSnapshot(
            Snapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "synth snapshot cannot be null");
        }
        Ym2612Chip.validateSnapshot(snapshot.ym());
        PsgChip.validateSnapshot(snapshot.psg());
        if (Double.compare(snapshot.outputSampleRate(),
                snapshot.ym().outputRate()) != 0
                || Double.compare(snapshot.outputSampleRate(),
                snapshot.psg().outputRate()) != 0) {
            throw new IllegalArgumentException(
                    "synth and chip output rates do not match");
        }
        if (snapshot.renderedYmMasterCycle()
                % YmWriteTimeline.MASTER_CYCLES_PER_INTERNAL_SAMPLE != 0) {
            throw new IllegalArgumentException(
                    "rendered YM frontier is not on an internal sample boundary");
        }

        YmWriteTimeline restoredTimeline = new YmWriteTimeline(
                YM_WRITE_TIMELINE_CAPACITY);
        restoredTimeline.restoreSnapshot(snapshot.ymWriteTimeline());
        for (YmWriteTimeline.Entry entry
                : snapshot.ymWriteTimeline().pending()) {
            if (entry.driverGeneration()
                    != snapshot.ymTimelineGeneration()) {
                throw new IllegalArgumentException(
                        "pending YM write generation does not match snapshot");
            }
        }
        return restoredTimeline.captureSnapshot();
    }

    /** Channel-bounded rollback state for one prepared SFX admission. */
    public static final class SfxAdmissionState {
        private final Ym2612Chip.SfxAdmissionState ym;
        private final PsgChip.SfxAdmissionState psg;

        private SfxAdmissionState(
                Ym2612Chip.SfxAdmissionState ym,
                PsgChip.SfxAdmissionState psg) {
            this.ym = ym;
            this.psg = psg;
        }
    }

    public SfxAdmissionState captureSfxAdmissionState(
            int affectedFmMask, int affectedPsgMask) {
        return new SfxAdmissionState(
                ym.captureSfxAdmissionState(affectedFmMask),
                psg.captureSfxAdmissionState(affectedPsgMask));
    }

    public void restoreSfxAdmissionState(SfxAdmissionState state) {
        ym.restoreSfxAdmissionState(state.ym);
        psg.restoreSfxAdmissionState(state.psg);
    }

    public void setDacData(DacData data) {
        ym.setDacData(data);
    }

    /**
     * Captures the identity-bearing DAC bank selected by a live command.
     * General synth/rewind snapshots intentionally omit this process-local
     * dependency and continue to resolve it through sequencer descriptors.
     */
    protected final DacData captureLiveDacDataReference() {
        return ym.liveDacDataReference();
    }

    /**
     * Restores a live command's exact DAC dependency before restoring YM
     * playback fields that resolve their current sample through that bank.
     */
    protected final void restoreLiveDacDataReference(DacData data) {
        ym.setDacData(data);
    }

    @Override
    public void playDac(Object source, int note) {
        ym.playDac(note);
    }

    @Override
    public void stopDac(Object source) {
        ym.stopDac();
    }

    public void render(short[] buffer) {
        renderFrames(buffer, 0, buffer.length / 2);
    }

    public void renderFrames(short[] buffer, int frameOffset, int frames) {
        if (buffer == null || frames <= 0) {
            return;
        }
        // Reuse scratch buffers, resize only when needed.
        if (scratchLeft.length < frames) {
            scratchLeft = new int[frames];
            scratchRight = new int[frames];
        }

        // Both chip renderers accumulate into the provided arrays.
        Arrays.fill(scratchLeft, 0, frames, 0);
        Arrays.fill(scratchRight, 0, frames, 0);

        ym.renderStereo(scratchLeft, scratchRight, frames);

        // GPGX-style: FM output is clipped to +/-8191 internally.
        // No output gain is applied here; volume issues are in EG/feedback.
        psg.renderStereo(scratchLeft, scratchRight, frames);

        int sampleOffset = frameOffset * 2;
        for (int i = 0; i < frames; i++) {
            int l = scratchLeft[i];
            int r = scratchRight[i];

            if (l > 32767) l = 32767; else if (l < -32768) l = -32768;
            if (r > 32767) r = 32767; else if (r < -32768) r = -32768;

            int sampleIndex = sampleOffset + (i * 2);
            buffer[sampleIndex] = (short) l;
            buffer[sampleIndex + 1] = (short) r;
        }

        RuntimeException observerFailure =
                ym.takePendingWriteObserverFailure();
        if (observerFailure != null) {
            throw observerFailure;
        }
    }

    @Override
    public void writeFm(Object source, int port, int reg, int val) {
        ym.write(port, reg, val);
    }

    @Override
    public void writePsg(Object source, int val) {
        psg.write(val);
    }

    @Override
    public void setInstrument(Object source, int channelId, byte[] voice) {
        ym.setInstrument(channelId, voice);
    }

    @Override
    public void setFmMute(int channel, boolean mute) {
        ym.setMute(channel, mute);
    }

    @Override
    public void setPsgMute(int channel, boolean mute) {
        psg.setMute(channel, mute);
    }

    @Override
    public void setDacInterpolate(boolean interpolate) {
        ym.setDacInterpolate(interpolate);
    }

    public void setPsgNoiseShiftOnEveryToggle(boolean everyToggle) {
        psg.setNoiseShiftOnEveryToggle(everyToggle);
    }

    public boolean isPsgNoiseShiftOnEveryToggle() {
        return psg.isNoiseShiftOnEveryToggle();
    }

    /** Selects the band-limited PSG path used by the BizHawk GPGX core. */
    protected final void setPsgHqMode(boolean enabled) {
        psg.setHqMode(enabled);
    }

    @Override
    public void silenceAll() {
        crossYmTimelineGenerationBarrier(
                YmTimelineGenerationBarrier.FULL_SILENCE);
        ym.silenceAll();
        psg.silenceAll();
    }

    /**
     * Force-silence an FM channel by directly resetting envelope state.
     * Used when SFX steals a channel to prevent chirp artifacts.
     */
    public void forceSilenceChannel(int channelId) {
        ym.forceSilenceChannel(channelId);
    }

    public record Snapshot(
            double outputSampleRate,
            Ym2612Chip.Snapshot ym,
            PsgChip.Snapshot psg,
            YmWriteTimeline.Snapshot ymWriteTimeline,
            long renderedYmMasterCycle,
            long ymTimelineGeneration) {
        public Snapshot {
            if (!Double.isFinite(outputSampleRate)
                    || outputSampleRate <= 0.0) {
                throw new IllegalArgumentException(
                        "output sample rate must be finite and positive");
            }
            Objects.requireNonNull(ym, "ym");
            Objects.requireNonNull(psg, "psg");
            Objects.requireNonNull(ymWriteTimeline, "ymWriteTimeline");
            if (renderedYmMasterCycle < 0) {
                throw new IllegalArgumentException(
                        "rendered YM master cycle cannot be negative");
            }
            if (ymTimelineGeneration < 0) {
                throw new IllegalArgumentException(
                        "YM timeline generation cannot be negative");
            }
        }
    }
}
