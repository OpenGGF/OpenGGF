package com.openggf.audio.debug;

import com.openggf.audio.AudioManager;
import com.openggf.audio.ChannelType;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.output.AudioPresentationSink;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.output.OpenAlPcmSink;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.PerformanceProfiler;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Isolated final-PCM presentation session used by the sound-test tools.
 */
public final class StandaloneAudioPresentationHost
        implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(
            StandaloneAudioPresentationHost.class.getName());

    private final String gameId;
    private final AudioManager manager;
    private final AtomicBoolean closed = new AtomicBoolean();

    private StandaloneAudioPresentationHost(
            String gameId, AudioManager manager) {
        this.gameId = gameId;
        this.manager = manager;
    }

    static StandaloneAudioPresentationHost fromManagerForTesting(
            String gameId, AudioManager manager) {
        return new StandaloneAudioPresentationHost(
                normalizeGameId(gameId), manager);
    }

    AudioManager managerForTesting() {
        return manager;
    }

    public static StandaloneAudioPresentationHost open(
            String gameId,
            SonicConfigurationService config,
            PerformanceProfiler profiler,
            boolean noDevice) {
        String boundGameId = normalizeGameId(gameId);
        GameAudioProfile profile = profile(boundGameId);
        AudioPresentationSink sink;
        AtomicReference<AudioManager> managerRef = new AtomicReference<>();
        if (noDevice) {
            sink = new NoDeviceAudioSink(48_000);
        } else {
            try {
                sink = OpenAlPcmSink.openDefault(
                        failure -> {
                            AudioManager manager = managerRef.get();
                            if (manager != null) {
                                manager.replaceFailedPresentationSink(failure);
                            } else {
                                LOGGER.log(Level.WARNING,
                                        "Sound-test speaker failed", failure);
                            }
                        },
                        warning -> LOGGER.warning(
                                "Sound-test speaker: " + warning));
            } catch (Throwable failure) {
                LOGGER.log(Level.WARNING,
                        "Sound-test device unavailable; using silent output",
                        failure);
                sink = new NoDeviceAudioSink(48_000);
            }
        }
        SmpsCoordFlagHandlerOwner owner =
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState());
        AudioManager manager;
        try {
            manager = AudioManager.createStandalonePresentation(
                    boundGameId, profile, config, profiler, sink, owner);
        } catch (Throwable failure) {
            try {
                sink.close();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        managerRef.set(manager);
        return new StandaloneAudioPresentationHost(boundGameId, manager);
    }

    public void playMusic(AbstractSmpsData data, DacData dac) {
        assertOpen();
        manager.playStandaloneMusic(data, dac);
    }

    public void playSfx(
            AbstractSmpsData data, DacData dac, float pitch) {
        assertOpen();
        manager.playStandaloneSfx(data, dac, pitch);
    }

    public void stopPlayback() {
        assertOpen();
        manager.stopStandalonePlayback();
    }

    public void toggleMute(ChannelType type, int channel) {
        assertOpen();
        manager.toggleMute(type, channel);
    }

    public void toggleSolo(ChannelType type, int channel) {
        assertOpen();
        manager.toggleSolo(type, channel);
    }

    public boolean isMuted(ChannelType type, int channel) {
        assertOpen();
        return manager.isMuted(type, channel);
    }

    public boolean isSoloed(ChannelType type, int channel) {
        assertOpen();
        return manager.isSoloed(type, channel);
    }

    public void setSpeedShoes(boolean enabled) {
        assertOpen();
        manager.setSpeedShoes(enabled);
    }

    public void presentFrame() {
        assertOpen();
        manager.presentShadowFrame(PresentationMode.FORWARD);
        manager.update();
    }

    String boundGameId() {
        return gameId;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            manager.destroy();
        }
    }

    private void assertOpen() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "standalone audio presentation host is closed");
        }
    }

    private static String normalizeGameId(String gameId) {
        if (gameId == null) {
            throw new IllegalArgumentException("gameId must not be null");
        }
        String normalized = gameId.toLowerCase(Locale.ROOT);
        if (!normalized.equals("s1")
                && !normalized.equals("s2")
                && !normalized.equals("s3k")) {
            throw new IllegalArgumentException(
                    "Unsupported game id: " + gameId);
        }
        return normalized;
    }

    private static GameAudioProfile profile(String gameId) {
        return SoundTestApp.createProfileForGame(gameId);
    }
}
