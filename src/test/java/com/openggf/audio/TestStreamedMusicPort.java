package com.openggf.audio;

import org.junit.jupiter.api.Test;

import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioSourceDescriptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamedMusicPort {
    @Test
    void emptyPortIsAStableSilentNoOp() {
        StreamedMusicPort empty = StreamedMusicPort.EMPTY;
        short[] pcm = { 7, -7 };

        assertFalse(empty.hasStockOverride(12));
        empty.playStockOverride(12);
        assertFalse(empty.hasSource());
        assertEquals(0, empty.mixInto(pcm, 1));
        assertArrayEquals(new short[] { 7, -7 }, pcm);
        assertTrue(empty.captureState().isEmpty());
        assertEquals(0, empty.outputRate());

        assertDoesNotThrow(() -> {
            empty.pause(StreamedMusicPort.PAUSE_APP);
            empty.resume(StreamedMusicPort.PAUSE_APP);
            empty.setSpeedMultiplier(2);
            empty.advanceFade();
            empty.stop();
            empty.reset();
            empty.close();
        });
    }

    @Test
    void neutralStateRejectsInvalidShapeWithoutDependingOnMods() {
        StreamedMusicPort.TrackRef key = new StreamedMusicPort.TrackRef("owner", "track");
        StreamedMusicPort.FadeState fade = StreamedMusicPort.FadeState.idle();
        StreamedMusicPort.State state = new StreamedMusicPort.State(key, 7, 2.5, 0, fade, 1.25);

        assertEquals("owner", state.track().owner());
        assertEquals("track", state.track().name());
        assertDoesNotThrow(() -> new StreamedMusicPort.State(key, -1, 0, 0, fade, 1),
                "-1 denotes a namespaced track with no numeric stock id");
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedMusicPort.State(key, -2, 0, 0, fade, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedMusicPort.State(key, 1, Double.NaN, 0, fade, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedMusicPort.State(key, 1, 0, 8, fade, 1));
    }

    @Test
    void emptyPortPreservesLivePortArgumentValidation() {
        assertThrows(NullPointerException.class, () -> StreamedMusicPort.EMPTY.mixInto(null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> StreamedMusicPort.EMPTY.mixInto(new short[1], 1));
        assertThrows(IllegalArgumentException.class, () -> StreamedMusicPort.EMPTY.pause(8));
        assertThrows(IllegalArgumentException.class, () -> StreamedMusicPort.EMPTY.resume(0));
        assertThrows(IllegalArgumentException.class, () -> StreamedMusicPort.EMPTY.fadeOut(0, 0));
        assertThrows(IllegalArgumentException.class, () -> StreamedMusicPort.EMPTY.fadeIn(1, -1));
        assertThrows(IllegalArgumentException.class, () -> StreamedMusicPort.EMPTY.setSpeedMultiplier(0));
        assertThrows(NullPointerException.class, () -> StreamedMusicPort.EMPTY.restoreState(null));
    }

    @Test
    void fadeStateRejectsUnreachableEndpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedMusicPort.FadeState(.5f, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedMusicPort.FadeState(.5f, 2, 0, 0, -.1f));
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedMusicPort.FadeState(.5f, 2, 0, 0, .1f));
        assertDoesNotThrow(() -> new StreamedMusicPort.FadeState(.5f, 2, 0, 0, -.25f));
        assertDoesNotThrow(() -> new StreamedMusicPort.FadeState(.5f, 2, 0, 0, .25f));
    }

    @Test
    void backendSnapshotRequiresSavedStreamStatesToAlignWithOverrideStack() {
        StreamedMusicPort.State state = new StreamedMusicPort.State(
                new StreamedMusicPort.TrackRef("owner", "track"), 1, 0, 0,
                StreamedMusicPort.FadeState.idle(), 1);
        assertThrows(IllegalArgumentException.class, () -> new AudioBackendLogicalSnapshot(
                AudioSourceDescriptor.baseMusic(1), false, false, false, 1,
                List.of(AudioSourceDescriptor.baseMusic(2)), null, null, state, List.of()));
    }
}
