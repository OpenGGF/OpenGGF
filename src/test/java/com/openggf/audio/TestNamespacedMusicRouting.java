package com.openggf.audio;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.game.MusicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Optional;

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
        RoutingPort port = new RoutingPort();
        audio.installStreamedMusicPort(port);
        audio.beginCommandTimelineFrame(9);

        assertTrue(audio.playNamespacedMusic(
                new StreamedMusicPort.TrackRef("owner", "level-theme")));

        assertEquals(new StreamedMusicPort.TrackRef("owner", "level-theme"),
                port.lastPlayedTrack);
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
        audio.resetState();
        audio.setBackend(backend);
        RoutingPort port = new RoutingPort();
        audio.installStreamedMusicPort(port);
        audio.beginCommandTimelineFrame(4);

        assertThrows(IllegalArgumentException.class, () -> audio.playNamespacedMusic(
                new StreamedMusicPort.TrackRef("owner", "missing")));

        assertEquals(0, audio.commandTimeline().entryCount());
        assertNull(port.lastPlayedTrack);
        assertEquals(0, backend.livePlayCount);
    }

    @Test
    void namespacedSfxIsExactAndPresentationOnly() {
        RecordingBackend backend = new RecordingBackend();
        audio.resetState();
        audio.setBackend(backend);
        RoutingPort port = new RoutingPort();
        audio.installStreamedMusicPort(port);
        audio.beginCommandTimelineFrame(6);
        StreamedMusicPort.SfxRef sfx = new StreamedMusicPort.SfxRef("owner", "jump");

        assertTrue(audio.playNamespacedSfx(sfx));

        assertEquals(sfx, port.lastSfxLookup);
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
        RoutingPort port = new RoutingPort();
        audio.installStreamedMusicPort(port);
        assertFalse(audio.playNamespacedSfx(new StreamedMusicPort.SfxRef("owner", "missing")));
        assertEquals(new StreamedMusicPort.SfxRef("owner", "missing"),
                port.lastSfxLookup);

        try (var ignored = audio.beginRewindReplay(10, 5,
                com.openggf.audio.rewind.AudioReplayReason.SEEK)) {
            assertFalse(audio.playNamespacedSfx(new StreamedMusicPort.SfxRef("owner", "jump")));
        }
        assertEquals(new StreamedMusicPort.SfxRef("owner", "missing"),
                port.lastSfxLookup, "rewind suppression occurs before port preflight");
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

    private static final class RoutingPort implements StreamedMusicPort {
        private TrackRef lastPlayedTrack;
        private SfxRef lastSfxLookup;
        private State state;

        @Override public int outputRate() { return 48_000; }
        @Override public boolean hasStockOverride(int musicId) { return false; }
        @Override public boolean isCurrentStockOverride(int musicId) { return false; }
        @Override public void playStockOverride(int musicId) {
            throw new IllegalArgumentException("no stock override");
        }
        @Override public boolean hasTrack(TrackRef track) {
            return "owner".equals(track.owner()) && !"missing".equals(track.name());
        }
        @Override public void playTrack(TrackRef track) {
            if (!hasTrack(track)) throw new IllegalArgumentException(track.toString());
            lastPlayedTrack = track;
            state = new State(track, -1, 0, 0, FadeState.idle(), 1);
        }
        @Override public boolean hasSfx(SfxRef sfx) {
            lastSfxLookup = sfx;
            return "owner".equals(sfx.owner()) && "jump".equals(sfx.name());
        }
        @Override public Optional<SfxPcm> sfxPcm(SfxRef sfx) {
            return hasSfx(sfx)
                    ? Optional.of(new SfxPcm(48_000, 1,
                    new short[]{100, 200}, 1)) : Optional.empty();
        }
        @Override public boolean hasSource() { return state != null; }
        @Override public int mixInto(short[] output, int frames) { return 0; }
        @Override public void pause(int reason) { }
        @Override public void resume(int reason) { }
        @Override public void fadeOut(int steps, int stepDelay) { }
        @Override public void fadeIn(int steps, int stepDelay) { }
        @Override public void advanceFade() { }
        @Override public boolean fadeActive() { return false; }
        @Override public boolean fadeAtFullGain() { return true; }
        @Override public void setSpeedMultiplier(int multiplier) { }
        @Override public void stop() { state = null; }
        @Override public void reset() { state = null; }
        @Override public Optional<State> captureState() {
            return Optional.ofNullable(state);
        }
        @Override public boolean restoreState(State restored) {
            state = restored;
            return hasTrack(restored.track());
        }
        @Override public void close() { state = null; }
    }
}
