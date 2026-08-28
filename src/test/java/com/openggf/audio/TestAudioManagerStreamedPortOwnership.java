package com.openggf.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
