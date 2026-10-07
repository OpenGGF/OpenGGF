package com.openggf.mods.mutators;

import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TestMutatorPreferenceStore {
    @TempDir Path temporary;

    @Test
    void roundTripRequestedValuesAndOrphansWithAtomicReplacement() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("prefs"));
        MutatorPreferenceStore store = new MutatorPreferenceStore(root, "s2");
        assertTrue(store.load().successful());
        MutatorPreferences preferences = new MutatorPreferences("s2", Map.of(
                "owner:gravity", new MutatorPreferences.Entry(1, false, Map.of("percent", 150)),
                "uninstalled:old", new MutatorPreferences.Entry(4, true, Map.of("choice", "old"))));
        assertInstanceOf(ModStateSaveResult.Saved.class, store.save(preferences));
        assertEquals(preferences, store.load().preferences());
        MutatorPreferences changed = new MutatorPreferences("s2", Map.of(
                "owner:gravity", new MutatorPreferences.Entry(1, true, Map.of("percent", 200))));
        assertInstanceOf(ModStateSaveResult.Saved.class, store.save(changed));
        assertEquals(changed, store.load().preferences());
        try (var files = Files.list(root)) { assertEquals(1, files.count()); }
    }

    @Test
    void malformedDuplicateUnknownFractionalAndOversizedInputFailsVisibly() throws Exception {
        Files.createDirectories(temporary.resolve("prefs"));
        MutatorPreferenceStore store = new MutatorPreferenceStore(temporary.resolve("prefs"), "s2");
        for (String malformed : List.of(
                "{}", "{\"formatVersion\":1,\"formatVersion\":1,\"profile\":\"s2\",\"entries\":{}}",
                "{\"formatVersion\":1,\"profile\":\"s2\",\"entries\":{},\"other\":true}",
                "{\"formatVersion\":1,\"profile\":\"s1\",\"entries\":{}}",
                "{\"formatVersion\":1,\"profile\":\"s2\",\"entries\":{\"owner:g\":{\"schemaVersion\":1,\"enabled\":true,\"options\":{\"p\":1.5}}}}",
                "{\"formatVersion\":1,\"profile\":\"s2\",\"entries\":{\"owner:g\":{\"schemaVersion\":1,\"enabled\":true,\"options\":{\"p\":null}}}}",
                "{\"formatVersion\":1,\"profile\":\"s2\",\"entries\":{} } trailing")) {
            Files.writeString(store.statePath(), malformed);
            assertFalse(store.load().successful(), malformed);
            assertTrue(store.load().preferences().entries().isEmpty());
            assertEquals(malformed, Files.readString(store.statePath()), "Failure preserves input for explicit recovery");
        }
        Files.writeString(store.statePath(), " ".repeat(262_145));
        assertFalse(store.load().successful());
    }

    @Test
    void schemaIdentityUnknownActiveOptionsAndRangesAreValidatedBeforeSessionAdmission() {
        MutatorDefinition definition = new MutatorDefinition("gravity", "Gravity", "", MutatorScope.LIVE,
                MutatorScope.LIVE, List.of(new MutatorOption.IntegerSlider("percent", "Gravity", "",
                MutatorScope.LIVE, 100, 25, 200, 1, "%")), Set.of(MutatorCapability.DRY_SONIC_GRAVITY),
                options -> List.of(new MutatorPolicy.DrySonicGravity(options.integer("percent"))));
        List<OwnedMutator> catalog = List.of(new OwnedMutator("owner", definition));
        for (MutatorPreferences.Entry invalid : List.of(
                new MutatorPreferences.Entry(2, true, Map.of("percent", 100)),
                new MutatorPreferences.Entry(1, true, Map.of("unknown", 100)),
                new MutatorPreferences.Entry(1, true, Map.of("percent", 201)))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new MutatorPreferences("s2", Map.of("owner:gravity", invalid)).forSession(catalog));
        }
        assertTrue(new MutatorPreferences("s2", Map.of("uninstalled:old",
                new MutatorPreferences.Entry(1, true, Map.of("unknown", 999)))).forSession(catalog).isEmpty());
    }

    @Test
    void symlinkRootAndDestinationAreRejectedWithoutTouchingTargets() throws Exception {
        Path other = Files.createDirectory(temporary.resolve("other"));
        Files.createSymbolicLink(temporary.resolve("linked"), other);
        MutatorPreferenceStore rootLink = new MutatorPreferenceStore(temporary.resolve("linked"), "s2");
        assertFalse(rootLink.load().successful());
        assertInstanceOf(ModStateSaveResult.Failed.class, rootLink.save(empty()));
        Path root = Files.createDirectory(temporary.resolve("safe"));
        Path sentinel = temporary.resolve("sentinel");
        Files.writeString(sentinel, "preserved");
        MutatorPreferenceStore fileLink = new MutatorPreferenceStore(root, "s2");
        Files.createSymbolicLink(fileLink.statePath(), sentinel);
        assertFalse(fileLink.load().successful());
        assertInstanceOf(ModStateSaveResult.Failed.class, fileLink.save(empty()));
        assertEquals("preserved", Files.readString(sentinel));
        assertTrue(Files.isSymbolicLink(fileLink.statePath()));
    }

    @Test
    void failedWritesKeepPreviousPreferenceFileAndCleanOnlyOwnedTemporary() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("prefs"));
        MutatorPreferenceStore original = new MutatorPreferenceStore(root, "s2");
        assertInstanceOf(ModStateSaveResult.Saved.class, original.save(empty()));
        String before = Files.readString(original.statePath());
        MutatorPreferenceStore broken = new MutatorPreferenceStore(root, "s2", new MutatorPreferenceStore.FileOperations() {
            @Override public void write(SeekableByteChannel channel, byte[] bytes) throws IOException {
                channel.write(java.nio.ByteBuffer.wrap(bytes, 0, 3));
                throw new IOException("injected disk failure");
            }
        });
        assertInstanceOf(ModStateSaveResult.Failed.class, broken.save(new MutatorPreferences("s2", Map.of(
                "owner:gravity", new MutatorPreferences.Entry(1, true, Map.of("percent", 150))))));
        assertEquals(before, Files.readString(original.statePath()));
        try (var files = Files.list(root)) { assertEquals(1, files.count()); }
    }

    @Test
    void rootIdentitySwapBeforePublicationCannotWriteReplacementRoot() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("prefs"));
        MutatorPreferenceStore original = new MutatorPreferenceStore(root, "s2");
        original.save(empty());
        String before = Files.readString(original.statePath());
        MutatorPreferenceStore racing = new MutatorPreferenceStore(root, "s2", new MutatorPreferenceStore.FileOperations() {
            @Override public void checkpoint(MutatorPreferenceStore.Boundary boundary, Path path) throws IOException {
                if (boundary == MutatorPreferenceStore.Boundary.BEFORE_MOVE) {
                    Files.move(root, temporary.resolve("moved"));
                    Files.createDirectory(root);
                    Files.writeString(root.resolve("sentinel"), "preserved");
                }
            }
        });
        assertInstanceOf(ModStateSaveResult.Failed.class, racing.save(empty()));
        assertFalse(Files.exists(racing.statePath()));
        assertEquals("preserved", Files.readString(root.resolve("sentinel")));
        assertEquals(before, Files.readString(temporary.resolve("moved").resolve(original.statePath().getFileName())));
        try (var files = Files.list(temporary.resolve("moved"))) { assertEquals(1, files.count()); }
    }

    private static MutatorPreferences empty() { return new MutatorPreferences("s2", Map.of()); }
}
