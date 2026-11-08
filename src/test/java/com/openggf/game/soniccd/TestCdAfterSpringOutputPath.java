package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

/** Actual filesystem controls for the observer's private output fence; no engine/session/GL work. */
class TestCdAfterSpringOutputPath {
    @TempDir Path directory;

    @Test void normalizationCannotHideTheExistingSymlinkTraversal() throws Exception {
        var root = directory.toRealPath();
        var foreign = Files.createDirectory(root.resolve("foreign"));
        var subdir = Files.createDirectory(foreign.resolve("subdir"));
        var foreignLeaf = Files.createDirectory(foreign.resolve("leaf"));
        Files.createSymbolicLink(root.resolve("link"), subdir);
        var raw = root.resolve("link/../leaf");
        assertEquals(foreignLeaf, raw.toRealPath());
        assertEquals(root.resolve("leaf"), raw.normalize());
        assertThrows(AssertionFailedError.class, () -> validate(raw, root));
        assertFalse(Files.exists(root.resolve("leaf")));
        try (var names = Files.list(foreignLeaf)) { assertEquals(0, names.count()); }
    }

    @Test void dotAndParentAliasesRejectBeforeResolvingMissingIntermediateDirectories() throws Exception {
        var root = directory.toRealPath();
        assertThrows(AssertionFailedError.class, () -> validate(root.resolve("missing/../leaf"), root));
        assertThrows(AssertionFailedError.class, () -> validate(root.resolve("./leaf"), root));
        assertThrows(AssertionFailedError.class, () -> validate(Path.of("leaf"), root));
        assertFalse(Files.exists(root.resolve("leaf")));
    }

    @Test void normalizedSymlinkParentStillCannotAliasTheCanonicalParent() throws Exception {
        var root = directory.toRealPath();
        var target = Files.createDirectory(root.resolve("target"));
        Files.createSymbolicLink(root.resolve("link"), target);
        assertThrows(AssertionFailedError.class, () -> validate(root.resolve("link/leaf"), root));
        assertFalse(Files.exists(target.resolve("leaf")));
    }

    @Test void canonicalLeafUsesTheSamePathForExclusiveCreationAndExport() throws Exception {
        var root = directory.toRealPath();
        var leaf = root.resolve("leaf");
        assertEquals(leaf, validate(leaf, root));
        Files.createDirectory(leaf);
        var output = leaf.resolve("probe.txt");
        Files.writeString(output, "owned", StandardOpenOption.CREATE_NEW);
        assertEquals("owned", Files.readString(root.resolve("leaf/probe.txt")));
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> Files.createDirectory(leaf));
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> Files.writeString(output, "replacement", StandardOpenOption.CREATE_NEW));
        assertEquals("owned", Files.readString(output));
    }

    private static Path validate(Path candidate, Path root) throws Exception {
        var method = TestSonicCdR31aLowerRoute.class.getDeclaredMethod("afterSpringOutputPath", String.class, Path.class);
        method.setAccessible(true);
        try { return (Path) method.invoke(null, candidate.toString(), root); }
        catch (InvocationTargetException failure) {
            var original = failure.getCause();
            if (original instanceof Error error) throw error;
            if (original instanceof Exception exception) throw exception;
            throw failure;
        }
    }
}
