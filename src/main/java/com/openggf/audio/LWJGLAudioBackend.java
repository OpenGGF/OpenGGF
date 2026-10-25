package com.openggf.audio;

import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;

import java.util.function.Consumer;

/**
 * LWJGL profile/source compatibility backend.
 *
 * <p>OpenAL ownership lives exclusively in {@link OpenAlPcmSink}. This class
 * does not own independent music, SFX, WAV, or streaming output sources.</p>
 */
public class LWJGLAudioBackend extends AbstractSmpsAudioBackend {
    private int deviceSampleRate = 48_000;

    public LWJGLAudioBackend(
            SonicConfigurationService configService,
            PerformanceProfiler profiler,
            MusicRestoreSink musicRestoreSink) {
        super(configService, profiler, musicRestoreSink);
    }

    /**
     * Uses the supplied manager's current factory for subsequent legacy bindings,
     * independently of the restore sink. The three-argument constructor retains
     * lazy per-binding singleton selection.
     */
    public LWJGLAudioBackend(
            SonicConfigurationService configService,
            PerformanceProfiler profiler,
            MusicRestoreSink musicRestoreSink,
            AudioManager legacyConfigAuthority) {
        super(configService, profiler, musicRestoreSink, legacyConfigAuthority);
    }

    @Override
    public AudioPresentationSink createPresentationSink(
            Consumer<Throwable> failureHandler,
            Consumer<String> warningHandler) {
        OpenAlPcmSink sink = OpenAlPcmSink.openDefault(
                failureHandler, warningHandler);
        deviceSampleRate = sink.sampleRate();
        return sink;
    }

    @Override
    protected int getDeviceSampleRate() {
        return deviceSampleRate;
    }

    @Override protected void hookInitDevice() {
    }

    @Override protected void hookDestroyDevice() {
    }

    @Override protected void hookStartStream() {
    }

    @Override protected void hookStopStreamSource() {
    }

    @Override protected void hookUpdateStream() {
    }

    @Override protected void hookStopAndClearMusicSource() {
    }

    @Override protected void hookStopAndUnqueueAllMusicBuffers() {
    }

    @Override protected void hookStopAndClearAllMusicBuffers() {
    }

    @Override protected void hookRestartStreamIfDry() {
    }

    @Override protected void hookStopAndDeleteWavSfxSources() {
    }

    @Override protected void hookPlayWavSfx(String sfxName, float pitch) {
    }

    @Override protected void hookCleanupStoppedWavSfx() {
    }

    @Override protected void hookPause() {
    }

    @Override protected void hookResume() {
    }

}
