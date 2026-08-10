package com.openggf.audio.synth;

import com.openggf.audio.smps.DacData;

import java.util.Arrays;

public class VirtualSynthesizer implements Synthesizer {
    private final PsgChip psg;
    private final Ym2612Chip ym;
    private double outputSampleRate = Ym2612Chip.getDefaultOutputRate();
    private boolean chipWriteObserverEnabled;

    // Output headroom: reduce overall level so 6 FM channels + PSG don't clip 16-bit output.
    private static final int MASTER_GAIN_SHIFT = 1; // -6 dB

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
        return new Snapshot(outputSampleRate, ym.captureSnapshot(), psg.captureSnapshot());
    }

    public void restoreSynthSnapshot(Snapshot snapshot) {
        outputSampleRate = snapshot.outputSampleRate();
        ym.restoreSnapshot(snapshot.ym());
        psg.restoreSnapshot(snapshot.psg());
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

            if (MASTER_GAIN_SHIFT > 0) {
                l >>= MASTER_GAIN_SHIFT;
                r >>= MASTER_GAIN_SHIFT;
            }

            if (l > 32767) l = 32767; else if (l < -32768) l = -32768;
            if (r > 32767) r = 32767; else if (r < -32768) r = -32768;

            int sampleIndex = sampleOffset + (i * 2);
            buffer[sampleIndex] = (short) l;
            buffer[sampleIndex + 1] = (short) r;
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

    @Override
    public void silenceAll() {
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
            PsgChip.Snapshot psg) {
    }
}
