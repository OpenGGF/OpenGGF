package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.channels.FileChannel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModStateStore {
    @TempDir
    Path temp;

    @Test
    void missingStateLoadsEmptyV1FromTheExactRepositoryChild() throws Exception {
        Path root = temp.toAbsolutePath().normalize();
        ModStateStore store = new ModStateStore(root);

        ModStateStore.LoadResult loaded = store.load();

        assertEquals(root.resolve("modstate.json"), store.statePath());
        assertEquals(ModState.EMPTY, loaded.state());
        assertTrue(loaded.quarantinedPath().isEmpty());
        assertFalse(Files.exists(temp.resolveSibling("modstate.json")));
        assertThrows(IllegalArgumentException.class, () -> new ModStateStore(Path.of("relative")));
    }

    @Test
    void stateValuesAreStrictUniqueDefensiveAndRoundTripAtomically() throws Exception {
        Path root = temp.toAbsolutePath().normalize();
        ModStateStore store = new ModStateStore(root);
        ModState state = new ModState(1, List.of(
                new ModState.Entry("second", false, 1),
                new ModState.Entry("first", true, 0)));

        assertInstanceOf(ModStateSaveResult.Saved.class, store.save(state));
        assertEquals(state, store.load().state());
        ModState replacement = new ModState(1, List.of(new ModState.Entry("replacement", true, 0)));
        assertInstanceOf(ModStateSaveResult.Saved.class, store.save(replacement));
        assertEquals(replacement, store.load().state());
        assertThrows(UnsupportedOperationException.class,
                () -> state.entries().add(new ModState.Entry("third", false, 2)));
        assertThrows(IllegalArgumentException.class, () -> new ModState(1, List.of(
                new ModState.Entry("same", true, 0), new ModState.Entry("same", false, 1))));
        assertThrows(IllegalArgumentException.class, () -> new ModState(1, List.of(
                new ModState.Entry("one", true, 0), new ModState.Entry("two", false, 0))));
        assertThrows(IllegalArgumentException.class,
                () -> new ModState.Entry("Invalid_ID", true, 0));
    }

    @Test
    void trustGrantRoundTripsWhileLegacyV1EntriesDefaultToUntrusted() throws Exception {
        Path root = temp.toAbsolutePath().normalize();
        String hash = "a".repeat(64);
        ModStateStore store = new ModStateStore(root);
        ModState trusted = new ModState(1, List.of(
                new ModState.Entry("code-mod", true, 0, true, hash)));

        assertInstanceOf(ModStateSaveResult.Saved.class, store.save(trusted));
        assertEquals(trusted, store.load().state());
        String encoded = Files.readString(root.resolve("modstate.json"));
        assertTrue(encoded.contains("\"trusted\":true"));
        assertTrue(encoded.contains("\"trustedJarSha256\":\"" + hash + "\""));

        Files.writeString(root.resolve("modstate.json"),
                "{\"formatVersion\":1,\"entries\":[{\"id\":\"legacy\","
                        + "\"enabled\":true,\"order\":0}]}");
        ModState.Entry legacy = store.load().state().entries().getFirst();
        assertFalse(legacy.trusted());
        assertEquals(null, legacy.trustedJarSha256());
        assertThrows(IllegalArgumentException.class, () -> new ModState.Entry(
                "bad-hash", true, 0, true, "not-a-sha256"));
    }

    @Test
    void malformedOrUnsafeJsonIsQuarantinedToAUniqueSiblingAndNeverTrusted() throws Exception {
        List<String> invalidDocuments = List.of(
                "not json",
                "{\"formatVersion\":2,\"entries\":[]}",
                "{\"formatVersion\":1}",
                "{\"formatVersion\":1,\"entries\":null}",
                "{\"formatVersion\":1,\"entries\":[null]}",
                "{\"formatVersion\":1,\"entries\":[{\"id\":\"one\",\"enabled\":true,\"order\":0},"
                        + "{\"id\":\"one\",\"enabled\":false,\"order\":1}]}",
                "{\"formatVersion\":1,\"entries\":[{\"id\":\"one\",\"enabled\":true,\"order\":0},"
                        + "{\"id\":\"two\",\"enabled\":false,\"order\":0}]}",
                "{\"formatVersion\":1,\"entries\":[{\"id\":\"Bad\",\"enabled\":true,\"order\":0}]}",
                "{\"formatVersion\":1,\"entries\":[{\"id\":\"one\",\"enabled\":\"yes\",\"order\":0}]}",
                "{\"formatVersion\":1,\"entries\":[],\"unsafe\":true}",
                "{\"formatVersion\":1,\"formatVersion\":1,\"entries\":[]}");

        for (int index = 0; index < invalidDocuments.size(); index++) {
            Path root = Files.createDirectory(temp.resolve("case-" + index)).toAbsolutePath().normalize();
            Path statePath = root.resolve("modstate.json");
            Files.writeString(statePath, invalidDocuments.get(index), StandardCharsets.UTF_8);

            ModStateStore.LoadResult loaded = new ModStateStore(root).load();

            assertEquals(ModState.EMPTY, loaded.state(), "case " + index);
            Path quarantine = loaded.quarantinedPath().orElseThrow();
            assertEquals(root, quarantine.getParent());
            assertTrue(quarantine.getFileName().toString().endsWith(".corrupt"));
            assertTrue(Files.exists(quarantine));
            assertFalse(Files.exists(statePath));
        }
    }

    @Test
    void hostileJsonLimitsAreAppliedBeforeMaterializingState() throws Exception {
        assertQuarantined(ModInputLimits.loweringBuilder().maxMetadataBytes(31).build(),
                "{\"formatVersion\":1,\"entries\":[]}");
        assertQuarantined(ModInputLimits.loweringBuilder().maxCollectionEntries(1).build(),
                "{\"formatVersion\":1,\"entries\":[{\"id\":\"one\",\"enabled\":true,\"order\":0},"
                        + "{\"id\":\"two\",\"enabled\":false,\"order\":1}]}");
        assertQuarantined(ModInputLimits.loweringBuilder().maxYamlDepth(2).build(),
                "{\"formatVersion\":1,\"entries\":[{\"id\":\"one\",\"enabled\":true,\"order\":0}]}");
    }

    @Test
    void constrainedTokenBudgetStillAcceptsCollectionsAtTheirInclusiveLimit() throws Exception {
        Path root = Files.createTempDirectory(temp, "inclusive-").toAbsolutePath().normalize();
        String entries = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> "{\"id\":\"mod-" + index
                        + "\",\"enabled\":false,\"order\":" + index + "}")
                .collect(java.util.stream.Collectors.joining(","));
        Files.writeString(root.resolve("modstate.json"),
                "{\"formatVersion\":1,\"entries\":[" + entries + "]}");
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxCollectionEntries(10).build();

        ModStateStore.LoadResult loaded = new ModStateStore(root, limits).load();

        assertEquals(10, loaded.state().entries().size());
        assertTrue(loaded.quarantinedPath().isEmpty());
    }

    @Test
    void writeAndMoveFailuresAreSurfacedAndTemporaryFilesAreCleaned() throws Exception {
        Path root = temp.toAbsolutePath().normalize();
        ModState state = new ModState(1, List.of(new ModState.Entry("one", true, 0)));
        ModStateStore.FileOperations writeFailure = new DelegatingOperations() {
            @Override
            public void write(FileChannel channel, byte[] bytes) throws IOException {
                throw new IOException("injected write failure");
            }
        };
        ModStateSaveResult.Failed failedWrite = assertInstanceOf(ModStateSaveResult.Failed.class,
                new ModStateStore(root, ModInputLimits.production(), writeFailure).save(state));
        assertTrue(failedWrite.message().contains("injected write failure"));
        assertNoTemporaryFiles(root);

        Path existing = root.resolve("modstate.json");
        Files.writeString(existing, "preserve-existing-target");
        java.util.concurrent.atomic.AtomicBoolean sawAtomic = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean sawReplace = new java.util.concurrent.atomic.AtomicBoolean();
        ModStateStore.FileOperations moveFailure = new DelegatingOperations() {
            @Override
            public void move(Path source, Path target, CopyOption... options) throws IOException {
                sawAtomic.set(List.of(options).contains(StandardCopyOption.ATOMIC_MOVE));
                sawReplace.set(List.of(options).contains(StandardCopyOption.REPLACE_EXISTING));
                throw new IOException("injected move failure");
            }
        };
        ModStateSaveResult.Failed failedMove = assertInstanceOf(ModStateSaveResult.Failed.class,
                new ModStateStore(root, ModInputLimits.production(), moveFailure).save(state));
        assertTrue(failedMove.message().contains("injected move failure"));
        assertTrue(sawAtomic.get());
        assertTrue(sawReplace.get());
        assertEquals("preserve-existing-target", Files.readString(existing));
        assertNoTemporaryFiles(root);
    }

    @Test
    void saveHonorsInjectedCollectionBoundsBeforeCreatingTheStateFile() {
        Path root = temp.toAbsolutePath().normalize();
        ModState state = new ModState(1, java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> new ModState.Entry("mod-" + index, false, index)).toList());
        ModInputLimits limits = ModInputLimits.loweringBuilder().maxCollectionEntries(3).build();

        assertInstanceOf(ModStateSaveResult.Failed.class, new ModStateStore(root, limits).save(state));
        assertFalse(Files.exists(root.resolve("modstate.json")));
    }

    @Test
    void quarantineFailureStillReturnsSafeEmptyState() throws Exception {
        Path root = temp.toAbsolutePath().normalize();
        Files.writeString(root.resolve("modstate.json"), "bad json");
        ModStateStore.FileOperations moveFailure = new DelegatingOperations() {
            @Override
            public void move(Path source, Path target, CopyOption... options) throws IOException {
                throw new IOException("quarantine denied");
            }
        };

        ModStateStore.LoadResult result = new ModStateStore(
                root, ModInputLimits.production(), moveFailure).load();

        assertEquals(ModState.EMPTY, result.state());
        assertTrue(result.quarantinedPath().isEmpty());
        assertTrue(result.message().orElseThrow().contains("quarantine denied"));
    }

    @Test
    void rootAndStateSymlinksAreNeverFollowed() throws Exception {
        Path parent = temp.toAbsolutePath().normalize();
        Path outside = Files.createDirectory(parent.resolve("outside"));
        Path outsideState = outside.resolve("modstate.json");
        Files.writeString(outsideState, "outside-must-remain");

        Path linkedRoot = parent.resolve("linked-root");
        createSymlinkOrAbort(linkedRoot, outside);
        ModStateStore linkedRootStore = new ModStateStore(linkedRoot);
        assertEquals(ModState.EMPTY, linkedRootStore.load().state());
        assertInstanceOf(ModStateSaveResult.Failed.class, linkedRootStore.save(ModState.EMPTY));
        assertEquals("outside-must-remain", Files.readString(outsideState));

        Path realRoot = Files.createDirectory(parent.resolve("real-root"));
        Path linkedState = realRoot.resolve("modstate.json");
        createSymlinkOrAbort(linkedState, outsideState);
        ModStateStore.LoadResult loaded = new ModStateStore(realRoot).load();
        assertEquals(ModState.EMPTY, loaded.state());
        assertEquals("outside-must-remain", Files.readString(outsideState));
        assertFalse(Files.exists(linkedState, java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertTrue(loaded.quarantinedPath().isPresent());
    }

    @Test
    void rootSwapAfterIdentityCaptureDoesNotQuarantineAReplacementDirectory() throws Exception {
        Path parent = temp.toAbsolutePath().normalize();
        Path root = Files.createDirectory(parent.resolve("mods"));
        Files.writeString(root.resolve("modstate.json"), "{\"formatVersion\":1,\"entries\":[]}");
        Path held = parent.resolve("held-mods");
        ModStateStore.FileOperations swapping = new DelegatingOperations() {
            private boolean swapped;

            @Override
            public void checkpoint(ModStateStore.Boundary boundary, Path path) throws IOException {
                if (!swapped && boundary == ModStateStore.Boundary.AFTER_ROOT_CAPTURE) {
                    Files.move(root, held);
                    Files.createDirectory(root);
                    Files.writeString(root.resolve("modstate.json"), "unrelated-replacement-state");
                    swapped = true;
                }
            }
        };
        try {
            ModStateStore store = new ModStateStore(root, ModInputLimits.production(), swapping);
            ModStateStore.LoadResult loaded = store.load();
            assertEquals(ModState.EMPTY, loaded.state());
            assertTrue(loaded.quarantinedPath().isEmpty());
            assertTrue(loaded.message().orElseThrow().contains("identity"));
            assertEquals("unrelated-replacement-state", Files.readString(root.resolve("modstate.json")));
        } finally {
            Files.deleteIfExists(root.resolve("modstate.json"));
            Files.deleteIfExists(root);
            if (Files.exists(held)) Files.move(held, root);
        }
    }

    @Test
    void rootSwapAfterTemporaryWriteFailsBeforeMoveAndCleansTheRealTemporaryFile() throws Exception {
        Path parent = temp.toAbsolutePath().normalize();
        Path root = Files.createDirectory(parent.resolve("save-root"));
        Path held = parent.resolve("held-save-root");
        ModStateStore.FileOperations swapping = new DelegatingOperations() {
            private boolean swapped;

            @Override
            public void checkpoint(ModStateStore.Boundary boundary, Path path) throws IOException {
                if (!swapped && boundary == ModStateStore.Boundary.AFTER_TEMP_WRITE) {
                    Files.move(root, held);
                    Files.createDirectory(root);
                    swapped = true;
                }
            }
        };
        try {
            ModStateSaveResult result = new ModStateStore(root, ModInputLimits.production(), swapping)
                    .save(new ModState(1, List.of(new ModState.Entry("one", true, 0))));
            assertInstanceOf(ModStateSaveResult.Failed.class, result);
            assertFalse(Files.exists(root.resolve("modstate.json")));
            assertNoTemporaryFiles(held);
        } finally {
            Files.deleteIfExists(root.resolve("modstate.json"));
            Files.deleteIfExists(root);
            if (Files.exists(held)) Files.move(held, root);
        }
    }

    @Test
    void replacingTempPathWithSymlinkBeforeWriteNeverWritesOutside() throws Exception {
        Path parent = temp.toAbsolutePath().normalize();
        Path root = Files.createDirectory(parent.resolve("temp-link-root"));
        Path outside = parent.resolve("outside-temp-target");
        Files.writeString(outside, "outside-original");
        ModStateStore.FileOperations swapping = new DelegatingOperations() {
            @Override
            public void checkpoint(ModStateStore.Boundary boundary, Path path) throws IOException {
                if (boundary == ModStateStore.Boundary.AFTER_TEMP_OPEN) {
                    Files.delete(path);
                    createSymlinkOrAbort(path, outside);
                }
            }
        };

        ModStateSaveResult result = new ModStateStore(root, ModInputLimits.production(), swapping)
                .save(new ModState(1, List.of(new ModState.Entry("one", true, 0))));

        assertInstanceOf(ModStateSaveResult.Failed.class, result);
        assertEquals("outside-original", Files.readString(outside));
    }

    @Test
    void moveSeamReplacingSourceWithSymlinkNeverPublishesOrWritesOutside() throws Exception {
        Path parent = temp.toAbsolutePath().normalize();
        Path root = Files.createDirectory(parent.resolve("move-link-root"));
        Path outside = parent.resolve("outside-move-target");
        Files.writeString(outside, "outside-original");
        ModStateStore.FileOperations swapping = new DelegatingOperations() {
            @Override
            public void move(Path source, Path target, CopyOption... options) throws IOException {
                Files.delete(source);
                createSymlinkOrAbort(source, outside);
                Files.move(source, target, options);
            }
        };

        ModStateSaveResult result = new ModStateStore(root, ModInputLimits.production(), swapping)
                .save(new ModState(1, List.of(new ModState.Entry("one", true, 0))));

        assertInstanceOf(ModStateSaveResult.Failed.class, result);
        assertEquals("outside-original", Files.readString(outside));
        assertFalse(Files.isRegularFile(root.resolve("modstate.json"),
                java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void failureImmediatelyAfterStageCreationLeavesNoPartialStage() throws Exception {
        Path root = Files.createDirectory(temp.resolve("stage-create-root")).toAbsolutePath().normalize();
        ModStateStore.FileOperations failure = new DelegatingOperations() {
            @Override
            public void checkpoint(ModStateStore.Boundary boundary, Path path) throws IOException {
                if (boundary == ModStateStore.Boundary.AFTER_STAGE_CREATE) {
                    throw new IOException("injected post-stage-create failure");
                }
            }
        };

        ModStateSaveResult result = new ModStateStore(root, ModInputLimits.production(), failure)
                .save(new ModState(1, List.of(new ModState.Entry("one", true, 0))));

        assertInstanceOf(ModStateSaveResult.Failed.class, result);
        assertNoStagingDirectories(root);
    }

    @Test
    void partialMarkerWriteFailureLeavesNoPartialStage() throws Exception {
        Path root = Files.createDirectory(temp.resolve("marker-write-root")).toAbsolutePath().normalize();
        ModStateStore.FileOperations failure = new DelegatingOperations() {
            @Override
            public void writeMarker(Path marker, String token) throws IOException {
                Files.writeString(marker, "partial", java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS);
                throw new IOException("injected partial marker failure");
            }
        };

        ModStateSaveResult result = new ModStateStore(root, ModInputLimits.production(), failure)
                .save(new ModState(1, List.of(new ModState.Entry("one", true, 0))));

        assertInstanceOf(ModStateSaveResult.Failed.class, result);
        assertNoStagingDirectories(root);
    }

    @Test
    void failureImmediatelyAfterTempChannelCreationClosesHandleAndCleansStage() throws Exception {
        Path root = Files.createDirectory(temp.resolve("channel-create-root")).toAbsolutePath().normalize();
        ModStateStore.FileOperations failure = new DelegatingOperations() {
            @Override
            public void checkpoint(ModStateStore.Boundary boundary, Path path) throws IOException {
                if (boundary == ModStateStore.Boundary.AFTER_TEMP_CHANNEL_CREATE) {
                    throw new IOException("injected post-channel-create failure");
                }
            }
        };

        ModStateSaveResult result = new ModStateStore(root, ModInputLimits.production(), failure)
                .save(new ModState(1, List.of(new ModState.Entry("one", true, 0))));

        assertInstanceOf(ModStateSaveResult.Failed.class, result);
        assertNoStagingDirectories(root);
        Files.delete(root);
        assertFalse(Files.exists(root));
    }

    private void assertQuarantined(ModInputLimits limits, String json) throws Exception {
        Path root = Files.createTempDirectory(temp, "limited-").toAbsolutePath().normalize();
        Files.writeString(root.resolve("modstate.json"), json);
        assertEquals(ModState.EMPTY, new ModStateStore(root, limits).load().state());
        assertFalse(Files.exists(root.resolve("modstate.json")));
    }

    private static void assertNoTemporaryFiles(Path root) throws IOException {
        try (var children = Files.list(root)) {
            assertTrue(children.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    private static void assertNoStagingDirectories(Path root) throws IOException {
        try (var children = Files.list(root)) {
            assertTrue(children.noneMatch(path ->
                    path.getFileName().toString().startsWith(".modstate-stage-")));
        }
    }

    private static class DelegatingOperations implements ModStateStore.FileOperations {
        @Override
        public void write(FileChannel channel, byte[] bytes) throws IOException {
            channel.write(java.nio.ByteBuffer.wrap(bytes));
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            Files.move(source, target, options);
        }
    }

    private static void createSymlinkOrAbort(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException error) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + error.getMessage());
        }
    }
}
