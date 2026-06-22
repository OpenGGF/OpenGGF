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

    @Test
    void sourcesDoNotReferenceDeletedSpawnCodecHelper() throws IOException {
        List<Path> staleReferences = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> contains(path, DELETED_HELPER_NAME))
                        .forEach(staleReferences::add);
            }
        }

        staleReferences.sort(Comparator.comparing(Path::toString));
        assertTrue(staleReferences.isEmpty(),
                "Deleted spawn-specific codec helper is still referenced in " + staleReferences);
    }

    private static boolean contains(Path path, String needle) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains(needle);
        } catch (IOException e) {
            throw new AssertionError("Failed to scan " + path, e);
        }
    }
}
