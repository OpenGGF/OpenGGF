package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.game.MusicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestNamespacedMusicRouting {
    private final AudioManager audio = AudioManager.getInstance();

    @AfterEach
    void tearDown() {
        audio.resetState();
    }

    @Test
    void musicReferenceKeepsStockAndNamespacedIdentitiesDisjoint() {
        assertEquals(0x81, ((MusicReference.Stock) MusicReference.stock(0x81)).musicId());
        assertEquals("one", ((MusicReference.Namespaced)
                MusicReference.namespaced("one", "shared")).owner());
        assertNotEquals(MusicReference.namespaced("one", "shared"),
                MusicReference.namespaced("two", "shared"));
        assertNotEquals(MusicReference.stock(1), MusicReference.namespaced("owner", "1"));
    }

    @Test
    void audioManagerRoutesNamespacedMusicWithoutAllocatingAStockId() {
        RecordingBackend backend = new RecordingBackend();
        audio.resetState();
        audio.setBackend(backend);
        audio.beginCommandTimelineFrame(9);

        assertTrue(audio.playNamespacedMusic(
                new StreamedMusicPort.TrackRef("owner", "level-theme")));

        assertEquals(new StreamedMusicPort.TrackRef("owner", "level-theme"), backend.track);
        assertEquals(-1, backend.stockMusicId);
        AudioCommand command = audio.commandTimeline().entries().getFirst().command();
        assertEquals(new AudioCommand.PlayNamespacedMusic(
                new StreamedMusicPort.TrackRef("owner", "level-theme")), command);
    }

    @Test
    void stockMusicReferencePreservesExistingNumericRoute() {
        RecordingBackend backend = new RecordingBackend();
        audio.resetState();
        audio.setBackend(backend);

        audio.playMusic(((MusicReference.Stock) MusicReference.stock(0x82)).musicId());

        assertEquals(0x82, backend.stockMusicId);
        assertNull(backend.track);
    }

    @Test
    void missingNamespacedTrackFailsBeforeTimelineOrPresentationMutation() {
        RecordingBackend backend = new RecordingBackend();
        backend.available = false;
        backend.track = new StreamedMusicPort.TrackRef("owner", "existing");
        audio.resetState();
        audio.setBackend(backend);
        audio.beginCommandTimelineFrame(4);

        assertThrows(IllegalArgumentException.class, () -> audio.playNamespacedMusic(
                new StreamedMusicPort.TrackRef("owner", "missing")));

        assertEquals(0, audio.commandTimeline().entryCount());
        assertEquals(new StreamedMusicPort.TrackRef("owner", "existing"), backend.track);
        assertEquals(0, backend.livePlayCount);
    }

    @Test
    void namespacedSfxIsExactAndPresentationOnly() {
        RecordingBackend backend = new RecordingBackend();
        audio.resetState();
        audio.setBackend(backend);
        audio.beginCommandTimelineFrame(6);
        StreamedMusicPort.SfxRef sfx = new StreamedMusicPort.SfxRef("owner", "jump");

        assertTrue(audio.playNamespacedSfx(sfx));

        assertEquals(sfx, backend.sfx);
        assertEquals(0, audio.commandTimeline().entryCount(),
                "one-shots must not enter deterministic audio history");
    }

    @Test
    void namespacedSfxIsSuppressedDuringRewindAndUnknownKeysDoNotMutatePresentation() {
        RecordingBackend backend = new RecordingBackend();
        audio.resetState();
        audio.setBackend(backend);
        backend.sfxAvailable = false;
        assertFalse(audio.playNamespacedSfx(new StreamedMusicPort.SfxRef("owner", "missing")));
        assertNull(backend.sfx);

        backend.sfxAvailable = true;
        try (var ignored = audio.beginRewindReplay(10, 5,
                com.openggf.audio.rewind.AudioReplayReason.SEEK)) {
            assertFalse(audio.playNamespacedSfx(new StreamedMusicPort.SfxRef("owner", "jump")));
        }
        assertNull(backend.sfx);
    }

    @Test
    void logicalReplayReconstructsNamespacedSnapshotWithoutLivePlayback() {
        RecordingBackend backend = new RecordingBackend();
        audio.resetState();
        audio.setBackend(backend);
        StreamedMusicPort.TrackRef track = new StreamedMusicPort.TrackRef("owner", "theme");

        audio.replayTimelineCommandLogically(new AudioCommand.PlayNamespacedMusic(track));

        assertEquals(0, backend.livePlayCount);
        StreamedMusicPort.State state = backend.snapshot.streamedMusic();
        assertNotNull(state);
        assertEquals(track, state.track());
        assertEquals(-1, state.logicalMusicId());
        assertEquals(0, state.sourceFramePosition());
        assertTrue(backend.snapshot.overrideStack().isEmpty());
        assertTrue(backend.snapshot.streamedOverrideStack().isEmpty());
    }

    private static final class RecordingBackend extends NullAudioBackend {
        private int stockMusicId = -1;
        private StreamedMusicPort.TrackRef track;
        private boolean available = true;
        private int livePlayCount;
        private StreamedMusicPort.SfxRef sfx;
        private boolean sfxAvailable = true;
        private AudioBackendLogicalSnapshot snapshot = AudioBackendLogicalSnapshot.empty();

        @Override
        public void playMusic(int musicId) {
            stockMusicId = musicId;
        }

        @Override
        public boolean tryPlayStreamedMusic(StreamedMusicPort.TrackRef track) {
            if (!available) return false;
            this.track = track;
            livePlayCount++;
            return true;
        }

        @Override
        public boolean hasStreamedMusic(StreamedMusicPort.TrackRef track) {
            return available;
        }

        @Override
        public boolean tryPlayStreamedSfx(StreamedMusicPort.SfxRef sfx) {
            if (!sfxAvailable) return false;
            this.sfx = sfx;
            return true;
        }

        @Override
        public AudioBackendLogicalSnapshot captureLogicalSnapshot() {
            return snapshot;
        }

        @Override
        public void restoreLogicalSnapshot(AudioBackendLogicalSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }
}
