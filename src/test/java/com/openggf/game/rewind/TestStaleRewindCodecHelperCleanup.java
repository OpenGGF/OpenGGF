package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards implementation comments against naming deleted dynamic-codec helpers.
 */
class TestStaleRewindCodecHelperCleanup {
    private static final String DELETED_HELPER_NAME = "exact" + "SpawnCodec";
    private static final String DELETED_SHARED_CODEC_CACHE =
            "SHARED_REWIND_DYNAMIC_OBJECT_" + "CODECS";
    private static final String DELETED_REGISTRY_CODEC_METHOD =
            "dynamicRewind" + "Codecs";
    private static final String DELETED_OBJECT_MANAGER_TEST_CODEC_REGISTRY =
            "TEST_OR_MIGRATION_REWIND_DYNAMIC_OBJECT_" + "CODECS";
    private static final String DELETED_OBJECT_MANAGER_TEST_CODEC_REGISTER =
            "registerRewindDynamicObjectCodec" + "ForTest";
    private static final String DELETED_OBJECT_MANAGER_TEST_CODEC_CLEAR =
            "clearRewindDynamicObjectCodecs" + "ForTest";
    private static final String DELETED_OBJECT_MANAGER_CODEC_LOOKUP =
            "rewindDynamicObjectCodec" + "For";
    private static final String DELETED_DYNAMIC_OBJECT_REWIND_CODEC =
            "DynamicObjectRewind" + "Codec";
    private static final String DELETED_DYNAMIC_CODEC_INVENTORY_OPTION =
            "--dynamic-" + "codec-inventory";
    private static final String DELETED_DYNAMIC_CODEC_INVENTORY_METHOD =
            "dynamic" + "CodecInventory";
    private static final String DELETED_DYNAMIC_CODEC_INVENTORY_ENTRY =
            "Dynamic" + "CodecInventoryEntry";
    private static final String DELETED_ALL_GAME_CODEC_CLASS_NAMES =
            "allGame" + "CodecClassNames";
    private static final String DELETED_PRINT_DYNAMIC_CODEC_INVENTORY =
            "printDynamic" + "CodecInventory";
    private static final String DELETED_S2_BATCH7_CLASSNAMES_CALL =
            "TestRewindFixS2Batch7Codecs.java";
    private static final String DELETED_S2_BATCH2_CLASSNAMES_CALL =
            "TestRewindFixS2Batch2Codecs.java";
    private static final String DELETED_S2_BATCH3_CLASSNAMES_CALL =
            "TestRewindFixS2Batch3Codecs.java";
    private static final String DELETED_S2_BATCH4_CLASSNAMES_CALL =
            "TestRewindFixS2Batch4Codecs.java";
    private static final String DELETED_DYNAMIC_CODECS_CLASSNAMES_CALL =
            "DeletedDynamicRewindCodecs." + "classNames()";
    private static final List<String> STALE_DYNAMIC_OBJECT_CODEC_PHRASES = List.of(
            "the " + "codec passes",
            "after the " + "codec recreates",
            "dynamic rewind " + "codec relinks",
            "Sonic3kObjectRegistry rewind " + "codec",
            "relinked by the " + "codec",
            "codec-driven " + "recreate",
            "rewind " + "codec recreates",
            "rewind " + "codec can re-derive",
            "the " + "codec recovers",
            "as the " + "codec did",
            "dynamic rewind " + "codecs and to",
            "NOT recreated by the " + "codec",
            "duplicate via a " + "codec",
            "codec " + "recreate would cause",
            "reuse / adopt / " + "codec",
            "codec's " + "recreate",
            "Shared dynamic-object rewind " + "codec factories",
            "object/" + "codec code",
            "deferred " + "codec behavior",
            "after " + "codec recreates",
            "parent-relink " + "codec",
            "mtzBossLaser" + "Codec",
            "hczEndBossChild" + "Codec",
            "codec-era " + "callers",
            "former explicit dynamic " + "codec",
            "dynamic " + "codec used",
            "deleted " + "codec did",
            "codec " + "recreates the",
            "codec " + "passes placeholders",
            "dynamic " + "codec (Phase-2",
            "codecs " + "in place");

    @Test
    void sourcesDoNotReferenceDeletedSpawnCodecHelper() throws IOException {
        assertNoSourceReferences(DELETED_HELPER_NAME,
                "Deleted spawn-specific codec helper is still referenced in ");
    }

    @Test
    void sourcesDoNotReferenceDeletedSharedCodecCache() throws IOException {
        assertNoSourceReferences(DELETED_SHARED_CODEC_CACHE,
                "Deleted shared dynamic-codec cache is still referenced in ");
    }

    @Test
    void sourcesDoNotReferenceDeletedRegistryCodecMethod() throws IOException {
        assertNoSourceReferences(DELETED_REGISTRY_CODEC_METHOD,
                "Deleted per-registry dynamic-codec method is still referenced in ");
    }

    @Test
    void sourcesDoNotReferenceDeletedObjectManagerCodecRegistry() throws IOException {
        assertNoSourceReferences(DELETED_OBJECT_MANAGER_TEST_CODEC_REGISTRY,
                "Deleted ObjectManager dynamic-codec registry is still referenced in ");
        assertNoSourceReferences(DELETED_OBJECT_MANAGER_TEST_CODEC_REGISTER,
                "Deleted ObjectManager dynamic-codec test register helper is still referenced in ");
        assertNoSourceReferences(DELETED_OBJECT_MANAGER_TEST_CODEC_CLEAR,
                "Deleted ObjectManager dynamic-codec test clear helper is still referenced in ");
        assertNoSourceReferences(DELETED_OBJECT_MANAGER_CODEC_LOOKUP,
                "Deleted ObjectManager dynamic-codec lookup helper is still referenced in ");
    }

    @Test
    void sourcesDoNotReferenceDeletedDynamicObjectRestoreCodecType() throws IOException {
        assertNoSourceReferences(DELETED_DYNAMIC_OBJECT_REWIND_CODEC,
                "Deleted dynamic-object rewind codec interface is still referenced in ");
    }

    @Test
    void sourcesDoNotReferenceDeletedDynamicCodecInventoryHelpers() throws IOException {
        assertNoSourceReferences(DELETED_DYNAMIC_CODEC_INVENTORY_OPTION,
                "Deleted dynamic-codec inventory CLI option is still referenced in ");
        assertNoSourceReferences(DELETED_DYNAMIC_CODEC_INVENTORY_METHOD,
                "Deleted dynamic-codec inventory helper is still referenced in ");
        assertNoSourceReferences(DELETED_DYNAMIC_CODEC_INVENTORY_ENTRY,
                "Deleted dynamic-codec inventory entry type is still referenced in ");
        assertNoSourceReferences(DELETED_ALL_GAME_CODEC_CLASS_NAMES,
                "Deleted game-codec class-name helper is still referenced in ");
        assertNoSourceReferences(DELETED_PRINT_DYNAMIC_CODEC_INVENTORY,
                "Deleted dynamic-codec inventory printer is still referenced in ");
    }

    @Test
    void s2Batch7TestDoesNotUseDeletedCodecClassNamesShim() throws IOException {
        assertNoSourceReferences(
                DELETED_DYNAMIC_CODECS_CLASSNAMES_CALL,
                DELETED_S2_BATCH7_CLASSNAMES_CALL,
                "Deleted test-only dynamic-codec classNames shim is still referenced in ");
    }

    @Test
    void s2Batch2TestDoesNotUseDeletedCodecClassNamesShim() throws IOException {
        assertNoSourceReferences(
                DELETED_DYNAMIC_CODECS_CLASSNAMES_CALL,
                DELETED_S2_BATCH2_CLASSNAMES_CALL,
                "Deleted test-only dynamic-codec classNames shim is still referenced in ");
    }

    @Test
    void s2Batch3TestDoesNotUseDeletedCodecClassNamesShim() throws IOException {
        assertNoSourceReferences(
                DELETED_DYNAMIC_CODECS_CLASSNAMES_CALL,
                DELETED_S2_BATCH3_CLASSNAMES_CALL,
                "Deleted test-only dynamic-codec classNames shim is still referenced in ");
    }

    @Test
    void s2Batch4TestDoesNotUseDeletedCodecClassNamesShim() throws IOException {
        assertNoSourceReferences(
                DELETED_DYNAMIC_CODECS_CLASSNAMES_CALL,
                DELETED_S2_BATCH4_CLASSNAMES_CALL,
                "Deleted test-only dynamic-codec classNames shim is still referenced in ");
    }

    @Test
    void sourcesDoNotDescribeGenericRecreateAsDynamicObjectCodecs() throws IOException {
        for (String phrase : STALE_DYNAMIC_OBJECT_CODEC_PHRASES) {
            assertNoSourceReferences(phrase,
                    "Stale dynamic-object codec wording is still referenced in ");
        }
    }

    private static void assertNoSourceReferences(String needle, String messagePrefix) throws IOException {
        assertNoSourceReferences(needle, null, messagePrefix);
    }

    private static void assertNoSourceReferences(
            String needle, String requiredFileName, String messagePrefix) throws IOException {
        List<Path> staleReferences = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> requiredFileName == null
                                || path.getFileName().toString().equals(requiredFileName))
                        .filter(path -> contains(path, needle))
                        .forEach(staleReferences::add);
            }
        }

        staleReferences.sort(Comparator.comparing(Path::toString));
        assertTrue(staleReferences.isEmpty(),
                messagePrefix + staleReferences);
    }

    private static boolean contains(Path path, String needle) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains(needle);
        } catch (IOException e) {
            throw new AssertionError("Failed to scan " + path, e);
        }
    }
}
