package com.openggf.mods;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.security.MessageDigest;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestModAudioPreparer {
    @TempDir Path temp;

    @Test
    void preparedValueTypesAreImmutableAndValidateIdentity() {
        PcmData pcm = PcmData.takeOwnership(8_000, 1, new short[] {1, 2});
        PreparedTrack track = new PreparedTrack(new TrackKey("owner", "music"), pcm,
                0, 2, 1.0f, false, "a".repeat(64));
        PreparedTrack nonLooping = new PreparedTrack(new TrackKey("owner", "once"), pcm,
                0, 0, 1.0f, false, "b".repeat(64));
        PreparedAudioSession session = new PreparedAudioSession(List.of(track), List.of(), java.util.Set.of());

        assertEquals(List.of(track), session.tracks());
        assertTrue(track.looping());
        assertFalse(nonLooping.looping());
        assertThrows(UnsupportedOperationException.class, () -> session.tracks().clear());
        assertThrows(IllegalArgumentException.class, () -> new PreparedAudioSession(
                List.of(track), List.of(), Set.of("INVALID")));
        assertThrows(IllegalArgumentException.class, () -> new PreparedAudioSession(
                List.of(track), List.of(), Set.of("owner")));
        assertThrows(IllegalArgumentException.class, () -> new PreparedTrack(track.key(), pcm,
                0, 3, 1.0f, false, "a".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new PreparedTrack(track.key(), pcm,
                0, 2, 1.0f, false, "BAD"));

        ModRuntimeFindingStore store = new ModRuntimeFindingStore();
        ModFinding finding = new ModFinding(ModFindingSeverity.ERROR, "AUDIO_DECODE_FAILED", "bad", null);
        store.replaceOwner("owner", List.of(finding));
        assertEquals(List.of(finding), store.findingsFor("owner"));
        store.replaceOwner("owner", List.of());
        assertTrue(store.findingsFor("owner").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> store.snapshot().clear());
    }

    @Test
    void preparesOnlyEffectiveOwnersInEffectiveThenTrackOrderAndReusesSourceRateCache() throws Exception {
        ModDescriptor first = descriptor("first", List.of(), Map.of(
                "audio/a.wav", wav(8_000, new short[] {0, 8_000, 16_000, 24_000}),
                "audio/b.wav", wav(8_000, new short[] {1, 2, 3, 4})));
        ModDescriptor second = descriptor("second", List.of(), Map.of(
                "audio/c.wav", wav(8_000, new short[] {5, 6, 7, 8})));
        ModDescriptor excluded = descriptor("excluded", List.of(), Map.of(
                "audio/x.wav", new byte[] {1, 2, 3}));
        ModTrackRegistry registry = new ModTrackRegistry(List.of(
                track("second", "c", "audio/c.wav", false, 0, OptionalLong.empty()),
                track("first", "b", "audio/b.wav", false, 0, OptionalLong.empty()),
                track("first", "eof", "audio/b.wav", true, 1, OptionalLong.empty()),
                track("excluded", "x", "audio/x.wav", false, 0, OptionalLong.empty()),
                track("first", "a", "audio/a.wav", true, 1, OptionalLong.of(3))));
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        RecordingSink sink = new RecordingSink(new ModStateSaveResult.Saved());
        ModAudioPreparer preparer = new ModAudioPreparer(root(), ModInputLimits.production(), findings, sink);

        PreparedAudioSession firstSession = preparer.prepare(
                new EffectiveModCatalog(List.of(first, second)), registry, 16_000);
        assertEquals(List.of(new TrackKey("first", "b"), new TrackKey("first", "eof"), new TrackKey("first", "a"),
                new TrackKey("second", "c")), firstSession.tracks().stream().map(PreparedTrack::key).toList());
        assertEquals(8, firstSession.tracks().get(1).loopEndFrame());
        PreparedTrack loop = firstSession.tracks().get(2);
        assertEquals(2, loop.loopStartFrame());
        assertEquals(6, loop.loopEndFrame());
        assertEquals(16_000, loop.pcm().sampleRate());
        assertEquals(sha256(wav(8_000, new short[] {0, 8_000, 16_000, 24_000})), loop.sourceSha256());
        assertTrue(firstSession.failedOwners().isEmpty());
        firstSession.close();
        PreparedAudioSession secondSession = preparer.prepare(
                new EffectiveModCatalog(List.of(first, second)), registry, 16_000);
        assertEquals(16_000, secondSession.tracks().get(0).pcm().sampleRate());
        secondSession.close();
        PreparedAudioSession differentRate = preparer.prepare(
                new EffectiveModCatalog(List.of(first, second)), registry, 8_000);

        assertEquals(8_000, differentRate.tracks().get(0).pcm().sampleRate());
        assertTrue(sink.calls.isEmpty());
        assertTrue(findings.snapshot().isEmpty());
        differentRate.close();
        Files.delete(first.jarPath());
        Files.delete(second.jarPath());
        Files.delete(excluded.jarPath());
    }

    @Test
    void ownerFailureDropsAllTracksAndTransitiveDependentsWhileIndependentSurvives() throws Exception {
        ModDescriptor broken = descriptor("broken", List.of(), Map.of(
                "audio/good.wav", wav(8_000, new short[] {1, 2}), "audio/bad.ogg", new byte[] {1, 2, 3}));
        ModDescriptor dependent = descriptor("dependent", List.of(dependency("broken")), Map.of(
                "audio/dep.wav", wav(8_000, new short[] {3, 4})));
        ModDescriptor leaf = descriptor("leaf", List.of(dependency("dependent")), Map.of(
                "audio/leaf.wav", wav(8_000, new short[] {5, 6})));
        ModDescriptor independent = descriptor("independent", List.of(), Map.of(
                "audio/ok.wav", wav(8_000, new short[] {7, 8})));
        ModTrackRegistry registry = new ModTrackRegistry(List.of(
                track("broken", "good", "audio/good.wav", false, 0, OptionalLong.empty()),
                track("broken", "bad", "audio/bad.ogg", false, 0, OptionalLong.empty()),
                track("dependent", "dep", "audio/dep.wav", false, 0, OptionalLong.empty()),
                track("leaf", "leaf", "audio/leaf.wav", false, 0, OptionalLong.empty()),
                track("independent", "ok", "audio/ok.wav", false, 0, OptionalLong.empty())));
        RecordingSink sink = new RecordingSink(new ModStateSaveResult.Saved());
        ModRuntimeFindingStore store = new ModRuntimeFindingStore();
        ModAudioPreparer preparer = new ModAudioPreparer(root(), ModInputLimits.production(), store, sink);
        Files.write(dependent.jarPath(), new byte[] {9, 8, 7});

        EffectiveModCatalog effective = new EffectiveModCatalog(List.of(broken, dependent, leaf, independent));
        PreparedAudioSession session = preparer.prepare(effective, registry, 8_000);

        assertEquals(List.of(new TrackKey("independent", "ok")),
                session.tracks().stream().map(PreparedTrack::key).toList());
        assertEquals(Set.of("broken", "dependent", "leaf"), session.failedOwners());
        assertEquals(List.of("broken", "dependent", "leaf"), new ArrayList<>(session.failedOwners()));
        assertEquals(List.of(Set.of("broken", "dependent", "leaf")), sink.calls);
        assertTrue(store.findingsFor("broken").stream().anyMatch(f -> f.code().equals("AUDIO_PREPARATION_FAILED")));
        assertTrue(store.findingsFor("dependent").stream().anyMatch(f -> f.code().equals("AUDIO_DEPENDENCY_FAILED")));
        assertTrue(store.findingsFor("leaf").stream().anyMatch(f -> f.code().equals("AUDIO_DEPENDENCY_FAILED")));
        assertFalse(session.findings().isEmpty());
        assertEquals(4, session.failedOwners().size() + session.tracks().size());
        assertEquals(List.of(broken, dependent, leaf, independent), effective.orderedEnabled());
        assertTrue(broken.findings().isEmpty());
    }

    @Test
    void multipleDirectFailuresCloseDiamondDeterministicallyWithOneDisableSaveCall() throws Exception {
        ModDescriptor brokenA = descriptor("broken-a", List.of(), Map.of("audio/a.ogg", new byte[] {1}));
        ModDescriptor brokenB = descriptor("broken-b", List.of(), Map.of("audio/b.ogg", new byte[] {2}));
        ModDescriptor left = descriptor("left", List.of(dependency("broken-a")), Map.of());
        ModDescriptor right = descriptor("right", List.of(dependency("broken-a"), dependency("broken-b")), Map.of());
        ModDescriptor diamond = descriptor("diamond", List.of(dependency("left"), dependency("right")), Map.of());
        RecordingSink sink = new RecordingSink(new ModStateSaveResult.Saved());
        ModAudioPreparer preparer = new ModAudioPreparer(root(), ModInputLimits.production(),
                new ModRuntimeFindingStore(), sink);

        PreparedAudioSession session = preparer.prepare(
                new EffectiveModCatalog(List.of(brokenA, brokenB, left, right, diamond)),
                new ModTrackRegistry(List.of(
                        track("broken-a", "bad", "audio/a.ogg", false, 0, OptionalLong.empty()),
                        track("broken-b", "bad", "audio/b.ogg", false, 0, OptionalLong.empty()))),
                8_000);

        assertEquals(List.of("broken-a", "broken-b", "left", "right", "diamond"),
                new ArrayList<>(session.failedOwners()));
        assertEquals(1, sink.calls.size());
        assertEquals(session.failedOwners(), sink.calls.getFirst());
        assertTrue(session.tracks().isEmpty());
        session.close();
    }

    @Test
    void identicalBytesUnderDifferentCodecExtensionsNeverCrossHitCache() throws Exception {
        byte[] wav = wav(8_000, new short[] {1, 2});
        ModDescriptor owner = descriptor("codec", List.of(), Map.of(
                "audio/valid.wav", wav, "audio/fake.ogg", wav));
        ModAudioPreparer preparer = new ModAudioPreparer(root(), ModInputLimits.production(),
                new ModRuntimeFindingStore(), new RecordingSink(new ModStateSaveResult.Saved()));

        PreparedAudioSession session = preparer.prepare(new EffectiveModCatalog(List.of(owner)),
                new ModTrackRegistry(List.of(
                        track("codec", "valid", "audio/valid.wav", false, 0, OptionalLong.empty()),
                        track("codec", "fake", "audio/fake.ogg", false, 0, OptionalLong.empty()))), 8_000);

        assertEquals(Set.of("codec"), session.failedOwners());
        assertTrue(session.tracks().isEmpty());
    }

    @Test
    void saveFailureIsVisibleAndLaterSuccessfulPrepareClearsStaleOwnerFinding() throws Exception {
        ModDescriptor owner = descriptor("owner", List.of(), Map.of("audio/bad.ogg", new byte[] {1, 2, 3}));
        ModDescriptor other = descriptor("other", List.of(), Map.of("audio/bad.ogg", new byte[] {4, 5, 6}));
        ModTrackRegistry badRegistry = new ModTrackRegistry(List.of(
                track("owner", "music", "audio/bad.ogg", false, 0, OptionalLong.empty()),
                track("other", "music", "audio/bad.ogg", false, 0, OptionalLong.empty())));
        ModRuntimeFindingStore store = new ModRuntimeFindingStore();
        RecordingSink sink = new RecordingSink(new ModStateSaveResult.Failed("disk full"));
        ModAudioPreparer preparer = new ModAudioPreparer(root(), ModInputLimits.production(), store, sink);

        PreparedAudioSession failed = preparer.prepare(new EffectiveModCatalog(List.of(owner, other)), badRegistry, 8_000);
        assertTrue(failed.findings().stream().anyMatch(f -> f.code().equals("MOD_STATE_SAVE_FAILED")));
        assertEquals(List.of("owner", "other"), new ArrayList<>(failed.failedOwners()));
        assertTrue(store.findingsFor("owner").stream().anyMatch(f -> f.code().equals("MOD_STATE_SAVE_FAILED")));
        assertTrue(store.findingsFor("other").stream().anyMatch(f -> f.code().equals("MOD_STATE_SAVE_FAILED")));
        failed.close();

        ModDescriptor repaired = descriptor("repaired", "owner", List.of(), Map.of(
                "audio/good.wav", wav(8_000, new short[] {1, 2})));
        PreparedAudioSession successful = preparer.prepare(new EffectiveModCatalog(List.of(repaired)),
                new ModTrackRegistry(List.of(track("owner", "music", "audio/good.wav",
                        false, 0, OptionalLong.empty()))), 8_000);
        assertTrue(successful.failedOwners().isEmpty());
        assertTrue(store.findingsFor("owner").isEmpty());
    }

    @Test
    void changedJarDigestFailsBeforeCacheReuseAndRootsCloseOnFailure() throws Exception {
        ModDescriptor owner = descriptor("digest", List.of(), Map.of(
                "audio/music.wav", wav(8_000, new short[] {1, 2})));
        ModTrackRegistry registry = new ModTrackRegistry(List.of(
                track("digest", "music", "audio/music.wav", false, 0, OptionalLong.empty())));
        ModAudioPreparer preparer = new ModAudioPreparer(root(), ModInputLimits.production(),
                new ModRuntimeFindingStore(), new RecordingSink(new ModStateSaveResult.Saved()));
        PreparedAudioSession original = preparer.prepare(new EffectiveModCatalog(List.of(owner)), registry, 8_000);
        assertFalse(original.tracks().isEmpty());
        original.close();
        writeJar(owner.jarPath(), Map.of("audio/music.wav", wav(8_000, new short[] {9, 10})));

        PreparedAudioSession changed = preparer.prepare(new EffectiveModCatalog(List.of(owner)), registry, 8_000);

        assertEquals(Set.of("digest"), changed.failedOwners());
        assertTrue(changed.findings().stream().anyMatch(f -> f.code().equals("MOD_JAR_CHANGED")));
        Files.delete(owner.jarPath());
    }

    @Test
    void cacheCountsIdenticalContentOnceAndFailsLaterOwnerDeterministicallyAtAggregateCap() throws Exception {
        short[] samples = new short[100];
        List<ModDescriptor> owners = new ArrayList<>();
        List<ModAudioTrack> tracks = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            String id = "cache-" + index;
            byte[] content = index < 2 ? wav(8_000, samples) : wav(8_000,
                    java.util.Arrays.copyOf(new short[] {(short) index}, 100));
            owners.add(descriptor(id, List.of(), Map.of("audio/music.wav", content)));
            tracks.add(track(id, "music", "audio/music.wav", false, 0, OptionalLong.empty()));
        }
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxAudioCacheBytes(1_087).build();
        ModAudioPreparer preparer = new ModAudioPreparer(root(), limits, new ModRuntimeFindingStore(),
                new RecordingSink(new ModStateSaveResult.Saved()));

        PreparedAudioSession session = preparer.prepare(new EffectiveModCatalog(owners),
                new ModTrackRegistry(tracks), 8_000);

        assertSame(session.tracks().get(0).pcm(), session.tracks().get(1).pcm());
        assertEquals(List.of("cache-2", "cache-3", "cache-4", "cache-5"),
                new ArrayList<>(session.failedOwners()));
        assertEquals(List.of("cache-0", "cache-1"),
                session.tracks().stream().map(track -> track.key().modId()).toList());
    }

    @Test
    void productionPendingSinkDisablesAndPersistsOnce() throws Exception {
        ModDescriptor owner = descriptor("pending", List.of(), Map.of());
        ModState startup = new ModState(1, List.of(new ModState.Entry("pending", true, 0)));
        ModStateStore stateStore = new ModStateStore(root());
        PendingModStateEditor editor = new PendingModStateEditor(startup, List.of(owner), stateStore);

        ModStateSaveResult result = ModAudioPreparer.FailureStateSink.pending(editor)
                .disableAndSave(Set.of("pending"));

        assertInstanceOf(ModStateSaveResult.Saved.class, result);
        assertFalse(editor.pendingState().entries().getFirst().enabled());
        assertFalse(stateStore.load().state().entries().getFirst().enabled());
    }

    @Test
    void sameOwnerProvisionalPcmReducesWorkingBudgetBeforeSecondAllocation() throws Exception {
        short[] firstSamples = new short[100];
        short[] secondSamples = new short[300];
        secondSamples[0] = 1;
        ModDescriptor owner = descriptor("provisional", List.of(), Map.of(
                "audio/first.wav", wav(8_000, firstSamples),
                "audio/second.wav", wav(8_000, secondSamples)));
        RecordingPcmProbe counter = new RecordingPcmProbe();
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxAudioCacheBytes(1_087).build();
        ModAudioPreparer preparer = new ModAudioPreparer(root(), limits, new ModRuntimeFindingStore(),
                new RecordingSink(new ModStateSaveResult.Saved()), new PcmDecoder(counter), new PcmResampler());

        PreparedAudioSession session = preparer.prepare(new EffectiveModCatalog(List.of(owner)),
                new ModTrackRegistry(List.of(
                        track("provisional", "first", "audio/first.wav", false, 0, OptionalLong.empty()),
                        track("provisional", "second", "audio/second.wav", false, 0, OptionalLong.empty()))), 8_000);

        assertEquals(1, counter.allocations);
        assertEquals(Set.of("provisional"), session.failedOwners());
        assertTrue(session.tracks().isEmpty());
        assertTrue(session.findings().stream().anyMatch(f ->
                        f.message().contains("Asset declared size") && f.message().contains("exceeds limit")),
                "The second asset must be rejected by readBounded's declared-size check before materialization");
    }

    @Test
    void activeSessionLeaseBlocksDifferentRateAllocationUntilCloseThenCacheEvicts() throws Exception {
        short[] samples = new short[100];
        ModDescriptor owner = descriptor("lease", List.of(), Map.of(
                "audio/music.wav", wav(8_000, samples)));
        ModTrackRegistry registry = new ModTrackRegistry(List.of(
                track("lease", "music", "audio/music.wav", false, 0, OptionalLong.empty())));
        RecordingPcmProbe allocations = new RecordingPcmProbe();
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxAudioCacheBytes(1_087).build();
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        RecordingSink sink = new RecordingSink(new ModStateSaveResult.Saved());
        ModAudioPreparer preparer = new ModAudioPreparer(root(), limits, findings,
                sink, new PcmDecoder(allocations), new PcmResampler());

        PreparedAudioSession rateA = preparer.prepare(new EffectiveModCatalog(List.of(owner)), registry, 8_000);
        assertThrows(IllegalStateException.class, () -> preparer.prepare(
                new EffectiveModCatalog(List.of(owner)), registry, 16_000));
        assertEquals(1, allocations.allocations);
        assertTrue(sink.calls.isEmpty());
        assertTrue(findings.snapshot().isEmpty());
        rateA.close();

        PreparedAudioSession rateB = preparer.prepare(new EffectiveModCatalog(List.of(owner)), registry, 16_000);
        assertEquals(2, allocations.allocations);
        assertFalse(rateB.tracks().isEmpty());
        rateB.close();
    }

    @Test
    void directEffectiveCatalogRejectsMissingDuplicateAndOutOfOrderDependencies() throws Exception {
        ModDescriptor base = descriptor("base-order", "base", List.of(), Map.of());
        ModDescriptor dependent = descriptor("dependent-order", "dependent", List.of(dependency("base")), Map.of());
        ModDescriptor missing = descriptor("missing-order", "missing", List.of(dependency("absent")), Map.of());
        ModDescriptor duplicate = descriptor("duplicate-order", "base", List.of(), Map.of());
        ModAudioPreparer preparer = new ModAudioPreparer(root(), ModInputLimits.production(),
                new ModRuntimeFindingStore(), new RecordingSink(new ModStateSaveResult.Saved()));

        assertThrows(IllegalArgumentException.class, () -> preparer.prepare(
                new EffectiveModCatalog(List.of(dependent, base)), ModTrackRegistry.EMPTY, 8_000));
        assertThrows(IllegalArgumentException.class, () -> preparer.prepare(
                new EffectiveModCatalog(List.of(missing)), ModTrackRegistry.EMPTY, 8_000));
        assertThrows(IllegalArgumentException.class, () -> preparer.prepare(
                new EffectiveModCatalog(List.of(base, duplicate)), ModTrackRegistry.EMPTY, 8_000));
    }

    private Path root() { return temp.toAbsolutePath().normalize(); }

    private ModDescriptor descriptor(String id, List<ModDependency> dependencies,
                                     Map<String, byte[]> entries) throws Exception {
        return descriptor(id, id, dependencies, entries);
    }

    private ModDescriptor descriptor(String jarName, String id, List<ModDependency> dependencies,
                                     Map<String, byte[]> entries) throws Exception {
        Path jar = temp.resolve(jarName + ".jar");
        writeJar(jar, entries);
        String digest;
        try (var assets = ModAssetRoot.jar(root(), jar)) { digest = assets.immutableSha256(); }
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"),
                List.of("Author"), "Description", VersionRange.parse("*"), ModType.PATCH, "s1",
                null, dependencies, Map.of(), Map.of(), null, OptionalInt.empty());
        return new ModDescriptor(jar, manifest, digest, false, List.of());
    }

    private static ModDependency dependency(String id) { return new ModDependency(id, VersionRange.parse("*")); }

    private static ModAudioTrack track(String owner, String id, String path, boolean loop,
                                       long start, OptionalLong end) {
        return new ModAudioTrack(new TrackKey(owner, id), path, loop, start, end, 1.0f, false);
    }

    private static byte[] wav(int rate, short[] samples) {
        ByteBuffer out = ByteBuffer.allocate(44 + samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952).putInt(out.capacity() - 8).putInt(0x45564157);
        out.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        out.putInt(0x61746164).putInt(samples.length * 2);
        for (short sample : samples) out.putShort(sample);
        return out.array();
    }

    private static void writeJar(Path path, Map<String, byte[]> entries) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class RecordingSink implements ModAudioPreparer.FailureStateSink {
        final List<Set<String>> calls = new ArrayList<>();
        final ModStateSaveResult result;
        RecordingSink(ModStateSaveResult result) { this.result = result; }
        @Override public ModStateSaveResult disableAndSave(Set<String> owners) {
            calls.add(Set.copyOf(owners));
            return result;
        }
    }

    private static final class RecordingPcmProbe implements PcmDecoder.AllocationProbe {
        int allocations;
        @Override public void beforeJavaPcmAllocation(long bytes) { allocations++; }
    }
}
