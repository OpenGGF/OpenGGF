package com.openggf.audio;

import com.openggf.audio.runtime.PcmHistoryRing;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;

/**
 * No-device SMPS audio backend for headless capture/replay.
 *
 * <p>This subclass runs the full {@link AbstractSmpsAudioBackend} synthesis,
 * sequencer, music-stack, SFX-lifecycle, snapshot and rewind machinery but
 * never touches an audio device: every device-output hook is a no-op. The
 * deterministic capture runtime (installed via
 * {@code attachDeterministicAudioRuntime}) reads the synthesized SMPS streams
 * directly and is the sole consumer of PCM, so the stream pump and buffer
 * upload hooks must do nothing — pumping here would steal samples from the
 * capture tap.
 *
 * <p>The synthesis sample rate is fixed at a deterministic 48000 Hz, with no
 * OpenAL device negotiation.
 */
public final class HeadlessSmpsAudioBackend extends AbstractSmpsAudioBackend {

    private static final int HEADLESS_SAMPLE_RATE = 48_000;

    public HeadlessSmpsAudioBackend(SonicConfigurationService configService, PerformanceProfiler profiler) {
        super(configService, profiler);
    }

    @Override
    protected int getDeviceSampleRate() {
        return HEADLESS_SAMPLE_RATE;
    }

    @Override
    public int outputSampleRate() {
        return HEADLESS_SAMPLE_RATE;
    }

    @Override
    protected void hookInitDevice() {
        // No device. Fix the synthesis sample rate and initialise any
        // non-device state the base needs (the PCM history rewind ring).
        pcmHistory = new PcmHistoryRing(Math.max(STREAM_BUFFER_SIZE,
                PcmHistoryRing.capacityFramesFor(
                        HEADLESS_SAMPLE_RATE,
                        configService.getString(SonicConfiguration.REWIND_AUDIO_HISTORY_LIMIT_TYPE),
                        configService.getInt(SonicConfiguration.REWIND_AUDIO_HISTORY_SECONDS),
                        configService.getInt(SonicConfiguration.REWIND_AUDIO_HISTORY_SIZE_MB))));
    }

    @Override
    protected void hookDestroyDevice() {
        // No device.
    }

    @Override
    protected void hookStartStream() {
        // No device: the capture runtime consumes synthesis directly.
    }

    @Override
    protected void hookStopStreamSource() {
        // No device.
    }

    @Override
    protected void hookUpdateStream() {
        // No device: skipping the stream pump leaves the full per-frame PCM
        // for the deterministic capture tap (keeps audio in sync).
    }

    @Override
    protected void hookStopAndClearMusicSource() {
        // No device.
    }

    @Override
    protected void hookStopAndUnqueueAllMusicBuffers() {
        // No device.
    }

    @Override
    protected void hookStopAndClearAllMusicBuffers() {
        // No device.
    }

    @Override
    protected void hookRestartStreamIfDry() {
        // No device.
    }

    @Override
    protected void hookUploadStreamBuffer(int bufferId, short[] pcm, int sampleRate) {
        // No device.
    }

    @Override
    protected void hookPlayWavSfx(String sfxName, float pitch) {
        // No device.
    }

    @Override
    protected void hookStopAndDeleteWavSfxSources() {
        // No device.
    }

    @Override
    protected void hookCleanupStoppedWavSfx() {
        // No device.
    }

    @Override
    protected void hookPause() {
        // No device.
    }

    @Override
    protected void hookResume() {
        // No device.
    }
}
