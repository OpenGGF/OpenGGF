package com.openggf.audio;

import com.openggf.ModStreamedMusicPort;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModAudioTrack;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModManifest;
import com.openggf.mods.ModTrackRegistry;
import com.openggf.mods.ModType;
import com.openggf.mods.PcmData;
import com.openggf.mods.PreparedAudioSession;
import com.openggf.mods.PreparedModMusic;
import com.openggf.mods.PreparedTrack;
import com.openggf.mods.SemanticVersion;
import com.openggf.mods.StreamedMusicPlayer;
import com.openggf.mods.TrackKey;
import com.openggf.mods.VersionRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Isolated
class TestAudioManagerStreamedPortOwnership {
    private final AudioManager audio = AudioManager.getInstance();

    @BeforeEach
    void setUp() {
        audio.setStreamedMusicSessionInvalidator(() -> { });
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
    }

    @AfterEach
    void tearDown() {
        audio.setStreamedMusicSessionInvalidator(() -> { });
        audio.resetState();
    }

    @Test
    void explicitClearClosesTransferredPortExactlyOnce() {
        CountingPort port = install();

        audio.resetStreamedMusicPort();
        audio.resetStreamedMusicPort();

        assertEquals(1, port.closeCount);
    }

    @Test
    void replacementClosesPriorPortAndLaterClearClosesReplacement() {
        CountingPort first = install();
        CountingPort second = new CountingPort();

        audio.installStreamedMusicPort(second);

        assertEquals(1, first.closeCount);
        assertEquals(0, second.closeCount);
        audio.resetStreamedMusicPort();
        assertEquals(1, second.closeCount);
    }

    @Test
    void resetStateClosesTransferredPortExactlyOnce() {
        CountingPort port = install();
        audio.resetState();
        assertEquals(1, port.closeCount);
    }

    @Test
    void backendSwapClosesTransferredPortExactlyOnce() {
        CountingPort port = install();
        audio.setBackend(new NullAudioBackend());
        assertEquals(1, port.closeCount);
    }

    @Test
    void launchBackendSwapClosesTransferredPortExactlyOnce() {
        CountingPort port = install();
        audio.setBackendForLaunch(new NullAudioBackend());
        assertEquals(1, port.closeCount);
    }

    @Test
    void destroyClosesTransferredPortExactlyOnce() {
        CountingPort port = install();
        audio.destroy();
        assertEquals(1, port.closeCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reset", "backend", "launch", "destroy"})
    void admittedNamespacedLoopAllowsOwnerTeardownAndFreshPlayback(String teardown) {
        ModStreamedMusicPort first = installAndRenderLoop();

        switch (teardown) {
            case "reset" -> audio.resetState();
            case "backend" -> audio.setBackend(new NullAudioBackend());
            case "launch" -> audio.setBackendForLaunch(new NullAudioBackend());
            case "destroy" -> audio.destroy();
            default -> throw new AssertionError(teardown);
        }

        verify(first, times(1)).close();
        assertThrows(IllegalStateException.class, first::captureState);
        audio.setBackend(new NullAudioBackend());
        ModStreamedMusicPort second = installAndRenderLoop();
        audio.resetState();
        verify(first, times(1)).close();
        verify(second, times(1)).close();
        assertThrows(IllegalStateException.class, second::captureState);
    }

    @Test
    void throwingInvalidatorStillClosesOnceAndKeepsReleaseFailureSuppressed() {
        CountingPort port = install();
        port.throwOnClose = true;
        IllegalStateException invalidation =
                new IllegalStateException("invalidation failed");
        audio.setStreamedMusicSessionInvalidator(() -> {
            throw invalidation;
        });

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class, audio::resetState);

        assertSame(invalidation, thrown);
        assertEquals(1, port.closeCount);
        assertEquals(1, thrown.getSuppressed().length);
        assertEquals("close failed", thrown.getSuppressed()[0].getMessage());
        audio.setStreamedMusicSessionInvalidator(() -> { });
    }

    private CountingPort install() {
        CountingPort port = new CountingPort();
        audio.installStreamedMusicPort(port);
        return port;
    }

    private ModStreamedMusicPort installAndRenderLoop() {
        int rate = audio.outputSampleRate();
        TrackKey key = new TrackKey("owner", "loop");
        PreparedTrack track = new PreparedTrack(key, PcmData.takeOwnership(rate, 1,
                new short[] {2048, -2048, 4096, -4096}), 0, 4, 1.0f, false, "a".repeat(64));
        ModManifest manifest = new ModManifest(1, "owner", "Owner", SemanticVersion.parse("1.0.0"),
                List.of("Test"), "Teardown ownership", VersionRange.parse("*"), ModType.PATCH,
                "s2", null, List.of(), Map.of(), Map.of(), null, OptionalInt.empty());
        ModDescriptor descriptor = new ModDescriptor(Path.of("owner.jar"), manifest,
                "b".repeat(64), false, List.of());
        ModTrackRegistry tracks = new ModTrackRegistry(List.of(new ModAudioTrack(key,
                "audio/loop.wav", true, 0, OptionalLong.of(4), 1.0f, false)));
        PreparedModMusic music = PreparedModMusic.build(new EffectiveModCatalog(List.of(descriptor)),
                tracks, new PreparedAudioSession(List.of(track), List.of(), Set.of()), rate);
        ModStreamedMusicPort port = spy(new ModStreamedMusicPort(music, new StreamedMusicPlayer(rate), "s2"));
        audio.installStreamedMusicPort(port);
        assertTrue(audio.playNamespacedMusic(new StreamedMusicPort.TrackRef("owner", "loop")));
        try (LiveCaptureAudioHandle capture = audio.beginLiveCaptureAudio(audio.presentationFrameRate())) {
            short[] pcm = new short[capture.maxStereoFramesPerPacket() * 2];
            audio.presentFrame(PresentationMode.FORWARD);
            int frames = capture.drainPresentationFrame(pcm);
            assertTrue(frames > 4, "the admitted voice must remain audible past the four-sample loop");
            boolean audible = false;
            for (int sample = 0; sample < frames * 2; sample++) audible |= pcm[sample] != 0;
            assertTrue(audible);
            var active = audio.captureLogicalSnapshot().presentation().activeMusic();
            assertNotNull(active);
            assertEquals(-1, active.musicId());
            assertEquals(AudioSourceDescriptor.streamedTrack("owner:loop"), active.sourceDescriptor());
            assertEquals(new StreamedMusicPort.TrackRef("owner", "loop"),
                    port.captureState().orElseThrow().track());
        }
        return port;
    }

    private static final class CountingPort implements StreamedMusicPort {
        private int closeCount;
        private boolean throwOnClose;

        @Override public int outputRate() { return 48_000; }
        @Override public boolean hasStockOverride(int musicId) { return false; }
        @Override public boolean isCurrentStockOverride(int musicId) { return false; }
        @Override public void playStockOverride(int musicId) { }
        @Override public boolean hasSource() { return false; }
        @Override public int mixInto(short[] output, int frames) { return 0; }
        @Override public void pause(int reason) { }
        @Override public void resume(int reason) { }
        @Override public void fadeOut(int steps, int stepDelay) { }
        @Override public void fadeIn(int steps, int stepDelay) { }
        @Override public void advanceFade() { }
        @Override public boolean fadeActive() { return false; }
        @Override public boolean fadeAtFullGain() { return true; }
        @Override public void setSpeedMultiplier(int multiplier) { }
        @Override public void stop() { }
        @Override public void reset() { }
        @Override public Optional<State> captureState() { return Optional.empty(); }
        @Override public boolean restoreState(State state) { return false; }
        @Override public void close() {
            closeCount++;
            if (throwOnClose) throw new IllegalStateException("close failed");
        }
    }
}
