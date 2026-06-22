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

    private static void assertNoSourceReferences(String needle, String messagePrefix) throws IOException {
        List<Path> staleReferences = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
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
