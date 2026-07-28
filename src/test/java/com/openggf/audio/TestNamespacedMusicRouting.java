package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
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

        audio.beginCommandTimelineFrame(11);
        audio.playMusic(((MusicReference.Stock) MusicReference.stock(0x82)).musicId());

        // The backend is not a live command sink any more, so a stock reference is
        // observable as its recorded numeric route rather than a backend call.
        AudioCommand stock = audio.commandTimeline().entries().getFirst().command();
        assertEquals(new AudioCommand.PlayMusic(
                0x82, AudioCommand.MusicRoute.FALLBACK_WAV, false, null), stock);
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
        assertEquals(1, audio.commandTimeline().entryCount(),
                "creator one-shots record like stock SFX rather than bypassing rewind");
        assertEquals(new AudioCommand.PlayNamespacedSfx(sfx),
                audio.commandTimeline().entries().getFirst().command());
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

        assertEquals(0, backend.livePlayCount,
                "logical replay must not trigger live playback");

        // Without an installed streamed-music port the presentation declines to
        // fabricate a voice rather than substituting silence, so the observable is
        // that replay stayed logical and no stock id was allocated.
        assertEquals(-1, backend.stockMusicId,
                "a namespaced replay must not fall back to a numeric stock route");
        assertTrue(audio.captureLogicalSnapshot().presentation().voices().stream()
                        .noneMatch(voice -> voice instanceof com.openggf.audio.presentation
                                .PresentationVoiceSnapshot.Streamed),
                "no streamed voice can exist without a port that vouches for the track");
    }

    private static final class RecordingBackend extends NullAudioBackend {
        private int stockMusicId = -1;
        private StreamedMusicPort.TrackRef track;
        private boolean available = true;
        private int livePlayCount;
        private StreamedMusicPort.SfxRef sfx;
        private boolean sfxAvailable = true;

        @Override
        public void playMusic(int musicId) {
            stockMusicId = musicId;
        }

        @Override
        public void playStreamedMusicOrElse(int musicId, Runnable stockFallback) {
            // Stock music reaches the backend through the override seam now; an
            // unresolved id falls through to the stock path.
            stockMusicId = musicId;
            stockFallback.run();
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
            if (available) {
                this.track = track;
            }
            return available;
        }

        @Override
        public boolean tryPlayStreamedSfx(StreamedMusicPort.SfxRef sfx) {
            if (!sfxAvailable) return false;
            this.sfx = sfx;
            return true;
        }

    }
}
