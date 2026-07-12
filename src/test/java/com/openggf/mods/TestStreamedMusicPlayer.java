package com.openggf.mods;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamedMusicPlayer {
    @Test
    void publicSnapshotDtosRejectInvalidPersistedShapes() {
        TrackKey key = new TrackKey("owner", "music");
        StreamedFadeSnapshot idle = new StreamedFadeSnapshot(1, 0, 0, 0, 0);
        assertThrows(NullPointerException.class,
                () -> new StreamedPlaybackSnapshot(null, 1, 0, 0, idle, 1));
        assertDoesNotThrow(() -> new StreamedPlaybackSnapshot(key, -1, 0, 0, idle, 1),
                "-1 is the namespaced-track sentinel and must round-trip through snapshots");
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedPlaybackSnapshot(key, 1, Double.NaN, 0, idle, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedPlaybackSnapshot(key, 1, 0, 8, idle, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedPlaybackSnapshot(key, 1, 0, 0, idle, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedFadeSnapshot(.5f, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StreamedFadeSnapshot(.5f, 2, 0, 0, -.1f));
        assertDoesNotThrow(() -> new StreamedFadeSnapshot(.5f, 2, 0, 0, -.25f));
        assertDoesNotThrow(() -> new StreamedFadeSnapshot(.5f, 2, 0, 0, .25f));
    }
    @Test
    void playIsIdempotentOnlyForSameLogicalIdAndTrackKey() {
        StreamedMusicPlayer player = new StreamedMusicPlayer(8_000);
        PreparedTrack first = prepared("first", false);
        player.play(10, first);
        player.mixInto(new short[4], 2);
        assertEquals(2.0, player.position());

        player.play(10, prepared("first", false));
        assertEquals(2.0, player.position());
        player.play(11, first);
        assertEquals(0.0, player.position());
        player.mixInto(new short[2], 1);
        player.play(11, prepared("second", false));
        assertEquals(0.0, player.position());
        assertThrows(IllegalArgumentException.class, () -> player.play(-1, prepared("third", false)));
        assertEquals(0.0, player.position());
    }

    @Test
    void independentPauseReasonsIncludingApplicationPauseAreIdempotent() {
        StreamedMusicPlayer player = playing(false);
        player.pause(StreamedMusicPlayer.PAUSE_JINGLE);
        player.pause(StreamedMusicPlayer.PAUSE_APP);
        player.pause(StreamedMusicPlayer.PAUSE_APP);
        assertEquals(StreamedMusicPlayer.PAUSE_JINGLE | StreamedMusicPlayer.PAUSE_APP, player.pauseMask());
        assertEquals(0, player.mixInto(new short[2], 1));
        player.resume(StreamedMusicPlayer.PAUSE_JINGLE);
        assertTrue(player.paused());
        player.resume(StreamedMusicPlayer.PAUSE_APP);
        assertFalse(player.paused());
        assertEquals(1, player.mixInto(new short[2], 1));

        assertThrows(IllegalArgumentException.class, () -> player.pause(0));
        assertThrows(IllegalArgumentException.class, () -> player.pause(8));
        assertThrows(IllegalArgumentException.class,
                () -> player.resume(StreamedMusicPlayer.PAUSE_APP | StreamedMusicPlayer.PAUSE_REWIND));
    }

    @Test
    void fadeUsesSmpsDelayConventionAndStopsAtLastStep() {
        StreamedMusicPlayer player = playing(false);
        player.fadeOut(2, 1);
        assertEquals(1.0f, player.fadeGain());
        player.mixInto(new short[2], 1);
        player.mixInto(new short[4], 2);
        assertEquals(1.0f, player.fadeGain(), "mix chunk size must not advance logical fade cadence");
        player.fadeOut(4, 0);
        player.advanceFade();
        assertEquals(0.75f, player.fadeGain(), "a new fade replaces the prior cadence from current gain");
        player.fadeOut(2, 1);
        player.advanceFade();
        assertEquals(0.75f, player.fadeGain());
        player.advanceFade();
        assertEquals(0.375f, player.fadeGain());
        assertTrue(player.playing());
        player.pause(StreamedMusicPlayer.PAUSE_APP);
        player.advanceFade();
        assertEquals(0.375f, player.fadeGain());
        player.resume(StreamedMusicPlayer.PAUSE_APP);
        player.advanceFade();
        assertEquals(0.375f, player.fadeGain());
        player.advanceFade();
        assertEquals(0.0f, player.fadeGain());
        assertFalse(player.playing());

        assertThrows(IllegalArgumentException.class, () -> playing(false).fadeOut(0, 1));
        assertThrows(IllegalArgumentException.class, () -> playing(false).fadeOut(1, -1));
    }

    @Test
    void tempoRateIsOnePointTwoFiveOnlyForOptedInTrackAndMultiplierAboveOne() {
        StreamedMusicPlayer optedIn = playing(true);
        optedIn.setSpeedMultiplier(2);
        assertEquals(1.25, optedIn.rate());
        optedIn.setSpeedMultiplier(1);
        assertEquals(1.0, optedIn.rate());

        StreamedMusicPlayer optedOut = playing(false);
        optedOut.setSpeedMultiplier(3);
        assertEquals(1.0, optedOut.rate());
        assertThrows(IllegalArgumentException.class, () -> optedOut.setSpeedMultiplier(0));
        assertThrows(IllegalArgumentException.class, () -> new StreamedMusicPlayer(7_999));
        assertThrows(IllegalArgumentException.class, () -> new StreamedMusicPlayer(192_001));
    }

    @Test
    void snapshotRestoreValidatesShapeKeyRateFadeAndOutputRateBeforeMutation() {
        StreamedMusicPlayer source = playing(true);
        source.mixInto(new short[4], 2);
        source.pause(StreamedMusicPlayer.PAUSE_REWIND);
        source.fadeOut(4, 2);
        source.advanceFade();
        source.setSpeedMultiplier(2);
        StreamedPlaybackSnapshot snapshot = source.capture().orElseThrow();

        StreamedMusicPlayer restored = new StreamedMusicPlayer(8_000);
        restored.restore(snapshot, key -> key.equals(snapshot.key()) ? prepared("music", true) : null);
        assertEquals(Optional.of(snapshot), restored.capture());

        StreamedPlaybackSnapshot wrongKey = new StreamedPlaybackSnapshot(
                new TrackKey("owner", "other"), 7, 1, 0,
                new StreamedFadeSnapshot(1, 0, 0, 0, 0), 1);
        assertThrows(IllegalArgumentException.class, () -> restored.restore(wrongKey, key -> null));
        assertEquals(Optional.of(snapshot), restored.capture());

        assertThrows(IllegalArgumentException.class, () -> restored.restore(
                state(snapshot, Double.NaN, snapshot.pauseMask(), snapshot.fade(), snapshot.rate()),
                key -> prepared("music", true)));
        assertThrows(IllegalArgumentException.class, () -> restored.restore(
                state(snapshot, snapshot.sourceFramePosition(), 8, snapshot.fade(), snapshot.rate()),
                key -> prepared("music", true)));
        assertThrows(IllegalArgumentException.class, () -> restored.restore(
                state(snapshot, snapshot.sourceFramePosition(), snapshot.pauseMask(),
                        new StreamedFadeSnapshot(1.1f, 1, 0, 0, -1), snapshot.rate()),
                key -> prepared("music", true)));
        assertThrows(IllegalArgumentException.class, () -> restored.restore(
                state(snapshot, snapshot.sourceFramePosition(), snapshot.pauseMask(),
                        new StreamedFadeSnapshot(0.5f, 0, 0, 0, 0), snapshot.rate()),
                key -> prepared("music", true)));
        assertThrows(IllegalArgumentException.class, () -> restored.restore(
                state(snapshot, snapshot.sourceFramePosition(), snapshot.pauseMask(),
                        new StreamedFadeSnapshot(0.5f, 1, 0, 0, -0.1f), snapshot.rate()),
                key -> prepared("music", true)));
        assertThrows(IllegalArgumentException.class, () -> restored.restore(
                state(snapshot, snapshot.sourceFramePosition(), snapshot.pauseMask(), snapshot.fade(), 2.0),
                key -> prepared("music", true)));
        assertDoesNotThrow(() -> new StreamedPlaybackSnapshot(snapshot.key(), -1,
                snapshot.sourceFramePosition(), snapshot.pauseMask(), snapshot.fade(), snapshot.rate()),
                "namespaced playback snapshots intentionally carry no stock music id");
        assertEquals(Optional.of(snapshot), restored.capture(), "invalid restore must be atomic");

        StreamedMusicPlayer wrongRate = new StreamedMusicPlayer(16_000);
        assertThrows(IllegalArgumentException.class, () -> wrongRate.play(1, prepared("music", false)));
        assertFalse(wrongRate.playing());
    }

    @Test
    void restoredMidFadePlaybackProducesIdenticalSubsequentPcmAndSnapshots() {
        PreparedTrack prepared = loopingPrepared();
        StreamedMusicPlayer original = new StreamedMusicPlayer(8_000);
        original.play(9, prepared);
        original.mixInto(new short[2], 1);
        original.fadeOut(4, 0);
        original.advanceFade();
        original.setSpeedMultiplier(2);
        original.pause(StreamedMusicPlayer.PAUSE_APP);
        original.pause(StreamedMusicPlayer.PAUSE_JINGLE);

        StreamedPlaybackSnapshot snapshot = original.capture().orElseThrow();
        StreamedMusicPlayer restored = new StreamedMusicPlayer(8_000);
        restored.restore(snapshot, key -> prepared);
        original.resume(StreamedMusicPlayer.PAUSE_APP);
        original.resume(StreamedMusicPlayer.PAUSE_JINGLE);
        restored.resume(StreamedMusicPlayer.PAUSE_APP);
        restored.resume(StreamedMusicPlayer.PAUSE_JINGLE);

        for (int tick = 0; tick < 3; tick++) {
            short[] expected = new short[2];
            short[] actual = new short[2];
            assertEquals(original.mixInto(expected, 1), restored.mixInto(actual, 1));
            assertArrayEquals(expected, actual);
            original.advanceFade();
            restored.advanceFade();
            assertEquals(original.capture(), restored.capture());
        }
    }

    @Test
    void naturalEndAndExplicitStopClearFadeState() {
        StreamedMusicPlayer natural = playing(false);
        natural.fadeOut(4, 0);
        natural.advanceFade();
        assertEquals(0.75f, natural.fadeGain());
        natural.mixInto(new short[8], 4);
        assertFalse(natural.playing());
        assertEquals(1.0f, natural.fadeGain());

        StreamedMusicPlayer stopped = playing(false);
        stopped.fadeOut(4, 0);
        stopped.advanceFade();
        stopped.stop();
        assertEquals(1.0f, stopped.fadeGain());
    }

    @Test
    void stopResetCloseAndThreadOwnershipAreSafe() throws Exception {
        StreamedMusicPlayer player = playing(true);
        player.pause(StreamedMusicPlayer.PAUSE_APP);
        player.fadeOut(2, 0);
        player.setSpeedMultiplier(2);
        player.stop();
        assertFalse(player.playing());
        assertEquals(StreamedMusicPlayer.PAUSE_APP, player.pauseMask());
        assertEquals(1.0, player.rate());
        assertEquals(1.0f, player.fadeGain());
        assertEquals(Optional.empty(), player.capture());

        player.play(1, prepared("music", true));
        assertEquals(1.25, player.rate());
        assertEquals(0, player.mixInto(new short[2], 1), "new play remains silent under global app pause");
        player.reset();
        assertFalse(player.playing());
        assertEquals(0, player.pauseMask());
        assertEquals(1.0, player.rate());
        player.close();
        player.close();
        assertTrue(player.closed());
        assertThrows(IllegalStateException.class, () -> player.play(1, prepared("music", false)));

        StreamedMusicPlayer owned = playing(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try { owned.mixInto(new short[2], 1); }
            catch (Throwable error) { failure.set(error); }
        });
        other.start();
        other.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
    }

    @Test
    void mixValidatesStereoCapacityAndDoesNotMutateStateOnFailure() {
        StreamedMusicPlayer player = playing(false);
        assertThrows(NullPointerException.class, () -> player.mixInto(null, 1));
        assertThrows(IllegalArgumentException.class, () -> player.mixInto(new short[1], 1));
        assertThrows(IllegalArgumentException.class, () -> player.mixInto(new short[2], -1));
        assertThrows(IllegalArgumentException.class,
                () -> player.mixInto(new short[2], Integer.MAX_VALUE));
        assertEquals(0.0, player.position());
    }

    @Test
    void playerNormalMixPathContainsNoAllocationIoStreamsOrCollections() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/mods/StreamedMusicPlayer.java"));
        String body = source.substring(source.indexOf("public int mixInto("), source.indexOf("public void pause("));
        assertFalse(body.contains("new short["));
        assertFalse(body.contains("new int["));
        assertFalse(body.contains("new ArrayList"));
        assertFalse(body.contains("new Hash"));
        assertFalse(body.contains(".stream("));
        assertFalse(body.contains("java.io"));
        assertFalse(body.contains("List<"));
        assertFalse(body.contains("Map<"));
    }

    @Test
    void fadeInUsesPositiveSignedStepAndRetainsPlaybackAtUnityGain() {
        StreamedMusicPlayer player = playing(false);

        player.fadeIn(4, 0);
        StreamedPlaybackSnapshot initial = player.capture().orElseThrow();
        assertEquals(0.0f, initial.fade().gain());
        assertEquals(0.25f, initial.fade().stepAmount());

        for (int step = 0; step < 4; step++) player.advanceFade();

        assertTrue(player.playing());
        assertEquals(1.0f, player.fadeGain());
        assertEquals(new StreamedFadeSnapshot(1.0f, 0, 0, 0, 0),
                player.capture().orElseThrow().fade());
        assertThrows(IllegalArgumentException.class, () -> player.fadeIn(0, 0));
        assertThrows(IllegalArgumentException.class, () -> player.fadeIn(1, -1));
    }

    @Test
    void publicSnapshotRestoreAcceptsAValidPositiveFadeAtomically() {
        StreamedMusicPlayer player = new StreamedMusicPlayer(8_000);
        StreamedPlaybackSnapshot snapshot = new StreamedPlaybackSnapshot(
                new TrackKey("owner", "music"), 7, 1.0, 0,
                new StreamedFadeSnapshot(0.5f, 2, 0, 0, 0.25f), 1.0);

        player.restore(snapshot, key -> prepared("music", false));

        assertEquals(Optional.of(snapshot), player.capture());
        player.advanceFade();
        player.advanceFade();
        assertEquals(1.0f, player.fadeGain());
        assertTrue(player.playing());
    }

    private static StreamedPlaybackSnapshot state(StreamedPlaybackSnapshot source,
                                                            double position, int mask,
                                                            StreamedFadeSnapshot fade, double rate) {
        return new StreamedPlaybackSnapshot(source.key(), source.logicalMusicId(), position, mask, fade, rate);
    }

    private static StreamedMusicPlayer playing(boolean tempoEffects) {
        StreamedMusicPlayer player = new StreamedMusicPlayer(8_000);
        player.play(7, prepared("music", tempoEffects));
        return player;
    }

    private static PreparedTrack prepared(String name, boolean tempoEffects) {
        return new PreparedTrack(new TrackKey("owner", name),
                PcmData.takeOwnership(8_000, 1, new short[] {1_000, 2_000, 3_000, 4_000}),
                0, 0, 1.0f, tempoEffects, "b".repeat(64));
    }

    private static PreparedTrack loopingPrepared() {
        return new PreparedTrack(new TrackKey("owner", "looping"),
                PcmData.takeOwnership(8_000, 1, new short[] {1_000, 2_000, 3_000, 4_000}),
                1, 4, 1.0f, true, "c".repeat(64));
    }
}
