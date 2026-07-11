package com.openggf.mods;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestModMusicResolver {
    @Test
    void isolatesNumericDomainsAndLaterEffectiveOwnerWins() {
        ModDescriptor s1Early = descriptor("s1-early", "s1", Map.of(12, "early"));
        ModDescriptor s2 = descriptor("s2-owner", "s2", Map.of(12, "music"));
        ModDescriptor s3k = descriptor("s3k-owner", "s3k", Map.of(12, "music"));
        ModDescriptor s1Late = descriptor("s1-late", "s1", Map.of(12, "late"));
        List<PreparedTrack> tracks = List.of(track("s1-early", "early", 8_000),
                track("s2-owner", "music", 8_000), track("s3k-owner", "music", 8_000),
                track("s1-late", "late", 8_000));
        PreparedAudioSession session = new PreparedAudioSession(tracks, List.of(), Set.of());
        PreparedModMusic music = PreparedModMusic.build(
                new EffectiveModCatalog(List.of(s1Early, s2, s3k, s1Late)), registry(tracks), session, 8_000);
        ModMusicResolver resolver = ModMusicResolver.from(music);

        assertEquals(new TrackKey("s1-late", "late"),
                resolver.resolveStockOverride("s1", 12).orElseThrow().track().key());
        assertEquals(new TrackKey("s2-owner", "music"),
                resolver.resolveStockOverride("s2", 12).orElseThrow().track().key());
        assertEquals(new TrackKey("s3k-owner", "music"),
                resolver.resolveStockOverride("s3k", 12).orElseThrow().track().key());
        assertEquals(12, resolver.resolveStockOverride("s1", 12).orElseThrow().logicalMusicId());
        assertEquals(8_000, music.outputRate());
        music.close();

        List<PreparedTrack> reversedTracks = List.of(track("s1-late", "late", 8_000),
                track("s1-early", "early", 8_000));
        PreparedModMusic reversed = PreparedModMusic.build(
                new EffectiveModCatalog(List.of(s1Late, s1Early)), registry(reversedTracks),
                new PreparedAudioSession(reversedTracks, List.of(), Set.of()), 8_000);
        assertEquals(new TrackKey("s1-early", "early"),
                ModMusicResolver.from(reversed).resolveStockOverride("s1", 12).orElseThrow().track().key());
        reversed.close();
    }

    @Test
    void directKeysRemainNamespacedAndOneTrackMayBackTwoStockIds() {
        ModDescriptor first = descriptor("first", "s1", Map.of(1, "shared", 2, "shared"));
        ModDescriptor second = descriptor("second", "s1", Map.of());
        List<PreparedTrack> tracks = List.of(track("first", "shared", 8_000),
                track("second", "shared", 8_000));
        PreparedModMusic music = PreparedModMusic.build(new EffectiveModCatalog(List.of(first, second)),
                registry(tracks), new PreparedAudioSession(tracks, List.of(), Set.of()), 8_000);
        ModMusicResolver resolver = ModMusicResolver.from(music);

        PreparedTrack firstTrack = resolver.resolve(new TrackKey("first", "shared")).orElseThrow();
        PreparedTrack secondTrack = resolver.resolve(new TrackKey("second", "shared")).orElseThrow();
        assertNotSame(firstTrack, secondTrack);
        assertSame(firstTrack, resolver.resolveStockOverride("s1", 1).orElseThrow().track());
        assertSame(firstTrack, resolver.resolveStockOverride("s1", 2).orElseThrow().track());
        music.close();
    }

    @Test
    void failedEffectiveOwnerIsSkippedButInconsistentTracksAndTargetsAreRejectedAtomically() {
        ModDescriptor failed = descriptor("failed", "s1", Map.of(1, "bad"));
        ModDescriptor healthy = descriptor("healthy", "s1", Map.of(2, "good"));
        PreparedTrack healthyTrack = track("healthy", "good", 8_000);
        PreparedAudioSession valid = new PreparedAudioSession(List.of(healthyTrack), List.of(), Set.of("failed"));
        PreparedModMusic music = PreparedModMusic.build(new EffectiveModCatalog(List.of(failed, healthy)),
                registry(List.of(track("failed", "bad", 8_000), healthyTrack)), valid, 8_000);
        assertTrue(ModMusicResolver.from(music).resolveStockOverride("s1", 1).isEmpty());
        assertTrue(ModMusicResolver.from(music).resolveStockOverride("s1", 2).isPresent());
        music.close();

        PreparedAudioSession missingTarget = new PreparedAudioSession(List.of(), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(healthy)), registry(List.of(healthyTrack)), missingTarget, 8_000));
        assertFalse(missingTarget.isClosed(), "failed construction must not take the session lease");

        PreparedAudioSession excludedTrack = new PreparedAudioSession(
                List.of(track("excluded", "music", 8_000)), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(healthy)), registry(List.of(healthyTrack)), excludedTrack, 8_000));
        assertFalse(excludedTrack.isClosed());

    }

    @Test
    void everyHealthyEffectiveRegistryTrackMustBePreparedEvenWhenNotAnOverrideTarget() {
        ModDescriptor owner = descriptor("owner", "s1", Map.of(1, "referenced"));
        PreparedTrack referenced = track("owner", "referenced", 8_000);
        PreparedTrack directOnly = track("owner", "direct-only", 8_000);
        PreparedAudioSession incomplete = new PreparedAudioSession(
                List.of(referenced), List.of(), Set.of());

        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(owner)), registry(List.of(referenced, directOnly)),
                incomplete, 8_000));

        assertFalse(incomplete.isClosed(), "failed construction must leave the caller's lease open");
        assertEquals(List.of(referenced), incomplete.tracks());
    }

    @Test
    void distinctTrackKeysMayShareExactPcmIdentityWithoutCloning() {
        PcmData shared = PcmData.takeOwnership(8_000, 1, new short[] {1, 2});
        PreparedTrack first = new PreparedTrack(new TrackKey("first", "music"), shared,
                0, 0, 1, false, "e".repeat(64));
        PreparedTrack second = new PreparedTrack(new TrackKey("second", "music"), shared,
                0, 0, 1, false, "e".repeat(64));
        PreparedModMusic music = PreparedModMusic.build(
                new EffectiveModCatalog(List.of(descriptor("first", "s1", Map.of()),
                        descriptor("second", "s1", Map.of()))),
                registry(List.of(first, second)),
                new PreparedAudioSession(List.of(first, second), List.of(), Set.of()), 8_000);
        ModMusicResolver resolver = ModMusicResolver.from(music);

        assertSame(first, resolver.resolve(first.key()).orElseThrow());
        assertSame(second, resolver.resolve(second.key()).orElseThrow());
        assertSame(shared, resolver.resolve(first.key()).orElseThrow().pcm());
        assertSame(shared, resolver.resolve(second.key()).orElseThrow().pcm());
        music.close();
    }

    @Test
    void validatesCatalogRegistryRateAndOpenSessionBeforePublishing() {
        ModDescriptor owner = descriptor("owner", "s1", Map.of(1, "music"));
        PreparedTrack prepared = track("owner", "music", 8_000);
        ModTrackRegistry registry = registry(List.of(prepared));

        PreparedAudioSession duplicateSession = new PreparedAudioSession(List.of(prepared), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(owner, owner)), registry, duplicateSession, 8_000));
        assertFalse(duplicateSession.isClosed());

        PreparedAudioSession missingRegistry = new PreparedAudioSession(List.of(prepared), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(owner)), ModTrackRegistry.EMPTY, missingRegistry, 8_000));
        assertFalse(missingRegistry.isClosed());

        PreparedAudioSession wrongRate = new PreparedAudioSession(List.of(prepared), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(owner)), registry, wrongRate, 16_000));
        assertFalse(wrongRate.isClosed());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                EffectiveModCatalog.EMPTY, ModTrackRegistry.EMPTY,
                new PreparedAudioSession(List.of(), List.of(), Set.of()), 7_999));

        PreparedAudioSession closed = new PreparedAudioSession(List.of(prepared), List.of(), Set.of());
        closed.close();
        assertThrows(IllegalStateException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(owner)), registry, closed, 8_000));
    }

    @Test
    void dependencyFirstCatalogAndKnownFailedOwnersAreRequired() {
        ModDescriptor base = descriptor("base", "s1", Map.of());
        ModDescriptor dependent = descriptor("dependent", "s1", Map.of(), List.of(dependency("base")));
        PreparedAudioSession empty = new PreparedAudioSession(List.of(), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(dependent, base)), ModTrackRegistry.EMPTY, empty, 8_000));
        assertFalse(empty.isClosed());

        ModDescriptor missing = descriptor("missing", "s1", Map.of(), List.of(dependency("absent")));
        PreparedAudioSession missingSession = new PreparedAudioSession(List.of(), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(missing)), ModTrackRegistry.EMPTY, missingSession, 8_000));
        assertFalse(missingSession.isClosed());

        ModDescriptor self = descriptor("self", "s1", Map.of(), List.of(dependency("self")));
        PreparedAudioSession selfSession = new PreparedAudioSession(List.of(), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(self)), ModTrackRegistry.EMPTY, selfSession, 8_000));
        assertFalse(selfSession.isClosed());

        PreparedAudioSession unknownFailed = new PreparedAudioSession(List.of(), List.of(), Set.of("unknown"));
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(base)), ModTrackRegistry.EMPTY, unknownFailed, 8_000));
        assertFalse(unknownFailed.isClosed());

        ModManifest standaloneManifest = new ModManifest(1, "standalone", "standalone",
                SemanticVersion.parse("1.0.0"), List.of("Author"), "Description", VersionRange.parse("*"),
                ModType.STANDALONE, null, null, List.of(), Map.of(1, "music"), Map.of(), null,
                OptionalInt.empty());
        ModDescriptor standalone = new ModDescriptor(Path.of("standalone.jar"), standaloneManifest,
                "d".repeat(64), false, List.of());
        PreparedTrack standaloneTrack = track("standalone", "music", 8_000);
        PreparedAudioSession standaloneSession = new PreparedAudioSession(
                List.of(standaloneTrack), List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> PreparedModMusic.build(
                new EffectiveModCatalog(List.of(standalone)), registry(List.of(standaloneTrack)),
                standaloneSession, 8_000));
        assertFalse(standaloneSession.isClosed());
    }

    @Test
    void snapshotsAreImmutableAndCloseInvalidatesBeforeReleasingSession() {
        ModDescriptor owner = descriptor("owner", "s1", Map.of(2, "music", 1, "music"));
        PreparedTrack track = track("owner", "music", 8_000);
        PreparedAudioSession session = new PreparedAudioSession(List.of(track), List.of(), Set.of());
        PreparedModMusic music = PreparedModMusic.build(new EffectiveModCatalog(List.of(owner)),
                registry(List.of(track)), session, 8_000);
        ModMusicResolver resolver = ModMusicResolver.from(music);

        assertThrows(UnsupportedOperationException.class,
                () -> music.trackIndexSnapshot().put(track.key(), 99));
        assertThrows(UnsupportedOperationException.class,
                () -> music.overrideSnapshot().get("s1").put(3, track.key()));
        assertEquals(List.of(1, 2), music.overrideSnapshot().get("s1").keySet().stream().toList());
        music.close();
        music.close();
        assertTrue(session.isClosed());
        assertThrows(IllegalStateException.class, () -> resolver.resolve(track.key()));
        assertThrows(IllegalStateException.class, () -> resolver.resolveStockOverride("s1", 1));
    }

    @Test
    void emptyParityAndControlledQueryValidation() {
        assertTrue(ModMusicResolver.EMPTY.resolve(new TrackKey("owner", "none")).isEmpty());
        assertTrue(ModMusicResolver.EMPTY.resolveStockOverride("s1", 0).isEmpty());
        assertThrows(NullPointerException.class, () -> ModMusicResolver.EMPTY.resolve(null));
        assertThrows(IllegalArgumentException.class,
                () -> ModMusicResolver.EMPTY.resolveStockOverride("S1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> ModMusicResolver.EMPTY.resolveStockOverride("s4", 0));
        assertThrows(IllegalArgumentException.class,
                () -> ModMusicResolver.EMPTY.resolveStockOverride("s1", -1));
        assertThrows(NullPointerException.class,
                () -> ModMusicResolver.EMPTY.resolveStockOverride(null, 0));
        PreparedTrack track = track("owner", "music", 8_000);
        assertThrows(IllegalArgumentException.class, () -> new ResolvedMusic(-1, track));
        assertThrows(NullPointerException.class, () -> new ResolvedMusic(1, null));
    }

    @Test
    void auditSeamsStayPackagePrivateAndTask8SourcesHaveNoIoDecodeBackendOrPcmCopyDependency()
            throws Exception {
        assertFalse(Modifier.isPublic(PreparedModMusic.class
                .getDeclaredMethod("trackIndexSnapshot").getModifiers()));
        assertFalse(Modifier.isPublic(PreparedModMusic.class
                .getDeclaredMethod("overrideSnapshot").getModifiers()));

        for (String file : List.of("PreparedModMusic.java", "ResolvedMusic.java", "ModMusicResolver.java")) {
            String source = Files.readString(Path.of("src/main/java/com/openggf/mods").resolve(file));
            assertFalse(source.contains("ModAssetRoot"), file);
            assertFalse(source.contains("PcmDecoder"), file);
            assertFalse(source.contains("com.openggf.audio"), file);
            assertFalse(source.contains("java.nio.file"), file);
            assertFalse(source.contains("copySamples("), file);
        }
    }

    @Test
    void concurrentReadOnlyResolutionReturnsStableTrackIdentityUntilSafeBoundaryClose() throws Exception {
        ModDescriptor owner = descriptor("owner", "s1", Map.of(1, "music"));
        PreparedTrack track = track("owner", "music", 8_000);
        PreparedModMusic music = PreparedModMusic.build(new EffectiveModCatalog(List.of(owner)),
                registry(List.of(track)), new PreparedAudioSession(List.of(track), List.of(), Set.of()), 8_000);
        ModMusicResolver resolver = ModMusicResolver.from(music);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] readers = new Thread[8];
        for (int index = 0; index < readers.length; index++) {
            readers[index] = new Thread(() -> {
                try {
                    for (int read = 0; read < 100; read++) {
                        assertSame(track, resolver.resolve(track.key()).orElseThrow());
                        assertSame(track, resolver.resolveStockOverride("s1", 1).orElseThrow().track());
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            readers[index].start();
        }
        for (Thread reader : readers) reader.join();
        assertNull(failure.get());

        music.close();
        assertThrows(IllegalStateException.class, () -> resolver.resolve(track.key()));
    }

    private static ModTrackRegistry registry(List<PreparedTrack> prepared) {
        return new ModTrackRegistry(prepared.stream().map(track -> new ModAudioTrack(track.key(),
                "audio/" + track.key().localName() + ".wav", false, 0,
                java.util.OptionalLong.empty(), track.gain(), track.tempoEffects())).toList());
    }

    private static PreparedTrack track(String owner, String name, int rate) {
        return new PreparedTrack(new TrackKey(owner, name),
                PcmData.takeOwnership(rate, 1, new short[] {1, 2}), 0, 0, 1, false, "a".repeat(64));
    }

    private static ModDescriptor descriptor(String id, String baseGame, Map<Integer, String> overrides) {
        return descriptor(id, baseGame, overrides, List.of());
    }

    private static ModDescriptor descriptor(String id, String baseGame, Map<Integer, String> overrides,
                                            List<ModDependency> dependencies) {
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"), List.of("Author"),
                "Description", VersionRange.parse("*"), ModType.PATCH, baseGame, null, dependencies,
                overrides, Map.of(), null, OptionalInt.empty());
        return new ModDescriptor(Path.of(id + ".jar"), manifest, "b".repeat(64), false, List.of());
    }

    private static ModDependency dependency(String id) {
        return new ModDependency(id, VersionRange.parse("*"));
    }
}
