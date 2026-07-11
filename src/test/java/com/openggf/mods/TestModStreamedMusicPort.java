package com.openggf.mods;

import com.openggf.ModStreamedMusicPort;
import com.openggf.audio.StreamedMusicPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TestModStreamedMusicPort {
    @Test
    void resolvesOnlyThePortsGameAndDelegatesPresentationControls() {
        Fixture fixture = fixture("s1", 12, false, player -> { });
        try (ModStreamedMusicPort port = fixture.port()) {
            assertEquals(8_000, port.outputRate());
            assertFalse(port.hasStockOverride(11));
            assertTrue(port.hasStockOverride(12));
            assertFalse(port.hasSource(), "availability queries must not mutate playback");
            port.playStockOverride(12);
            assertTrue(port.hasSource());
            assertEquals(1, port.mixInto(new short[2], 1));
            port.pause(StreamedMusicPort.PAUSE_JINGLE);
            assertEquals(0, port.mixInto(new short[2], 1));
            port.resume(StreamedMusicPort.PAUSE_JINGLE);
            port.fadeOut(2, 0);
            port.advanceFade();
            assertEquals(0.5f, port.captureState().orElseThrow().fade().gain());
            port.stop();
            assertFalse(port.hasSource());
        }
    }

    @Test
    void mapsSnapshotsAtTheBoundaryAndUnavailableRestoreIsAtomic() {
        Fixture fixture = fixture("s2", 7, true, player -> { });
        try (ModStreamedMusicPort port = fixture.port()) {
            assertTrue(port.hasStockOverride(7));
            port.playStockOverride(7);
            port.mixInto(new short[4], 2);
            port.fadeIn(4, 0);
            port.advanceFade();
            port.pause(StreamedMusicPort.PAUSE_REWIND);
            port.setSpeedMultiplier(2);
            StreamedMusicPort.State state = port.captureState().orElseThrow();
            assertEquals(new StreamedMusicPort.TrackRef("owner", "music"), state.track());
            assertEquals(new StreamedMusicPort.FadeState(0.25f, 3, 0, 0, 0.25f), state.fade());
            assertEquals(1.25, state.rate());

            port.stop();
            assertTrue(port.restoreState(state));
            assertEquals(state, port.captureState().orElseThrow());

            StreamedMusicPort.State missing = new StreamedMusicPort.State(
                    new StreamedMusicPort.TrackRef("owner", "missing"), 7, 0, 0,
                    StreamedMusicPort.FadeState.idle(), 1.0);
            assertFalse(port.restoreState(missing));
            assertEquals(state, port.captureState().orElseThrow(), "failed restore must not mutate playback");
        }
    }

    @Test
    void closeClosesPlayerBeforeReleasingOwnedPreparedMusicExactlyOnce() {
        AtomicInteger releases = new AtomicInteger();
        AtomicBoolean playerWasClosedAtRelease = new AtomicBoolean();
        Fixture fixture = fixture("s3k", 3, false, player -> {
            releases.incrementAndGet();
            playerWasClosedAtRelease.set(player.closed());
        });

        fixture.port().close();
        fixture.port().close();

        assertTrue(fixture.player().closed());
        assertTrue(fixture.music().isClosed());
        assertTrue(playerWasClosedAtRelease.get());
        assertEquals(1, releases.get());
    }

    private static Fixture fixture(String gameCode, int musicId, boolean tempoEffects,
                                   Consumer<StreamedMusicPlayer> release) {
        PreparedTrack track = new PreparedTrack(new TrackKey("owner", "music"),
                PcmData.takeOwnership(8_000, 1, new short[] {1, 2, 3, 4}),
                0, 0, 1.0f, tempoEffects, "a".repeat(64));
        ModManifest manifest = new ModManifest(1, "owner", "owner", SemanticVersion.parse("1.0.0"),
                List.of("Author"), "Description", VersionRange.parse("*"), ModType.PATCH, gameCode,
                null, List.of(), Map.of(musicId, "music"), Map.of(), null, OptionalInt.empty());
        ModDescriptor descriptor = new ModDescriptor(Path.of("owner.jar"), manifest,
                "b".repeat(64), false, List.of());
        ModTrackRegistry registry = new ModTrackRegistry(List.of(new ModAudioTrack(track.key(),
                "audio/music.wav", false, 0, java.util.OptionalLong.empty(), 1.0f, tempoEffects)));
        StreamedMusicPlayer player = new StreamedMusicPlayer(8_000);
        PreparedAudioSession session = new PreparedAudioSession(
                List.of(track), List.of(), Set.of(), () -> release.accept(player));
        PreparedModMusic music = PreparedModMusic.build(new EffectiveModCatalog(List.of(descriptor)),
                registry, session, 8_000);
        return new Fixture(music, player, new ModStreamedMusicPort(music, player, gameCode));
    }

    private record Fixture(PreparedModMusic music, StreamedMusicPlayer player,
                           ModStreamedMusicPort port) { }
}
