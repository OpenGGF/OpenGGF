package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestObjectGuardSourceScanner {
    @TempDir Path root;

    @Test
    void preservesOptionalRootsRecursiveTraversalAndPortableOrdering() throws Exception {
        Path nested = Files.createDirectory(root.resolve("a"));
        Path last = Files.writeString(root.resolve("z.java"), "");
        Path first = Files.writeString(nested.resolve("A.java"), "");
        Files.writeString(root.resolve("ignored.txt"), "");
        // Existing guards filter suffixes rather than regular files; preserve that contract.
        Path javaDirectory = Files.createDirectory(root.resolve("d.java"));
        assertEquals(List.of(first, javaDirectory, last),
                ObjectGuardSourceScanner.sortedJavaSources(root));
        assertEquals(List.of(), ObjectGuardSourceScanner.sortedJavaSources(root.resolve("missing")));
        assertEquals(List.of(last), ObjectGuardSourceScanner.sortedJavaSources(last));
        assertThrows(UnsupportedOperationException.class,
                () -> ObjectGuardSourceScanner.sortedJavaSources(root).clear());
    }
}
