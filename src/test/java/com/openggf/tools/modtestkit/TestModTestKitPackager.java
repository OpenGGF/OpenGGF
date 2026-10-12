package com.openggf.tools.modtestkit;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestModTestKitPackager {
    @TempDir Path work;

    @Test void stagesOnlyToolkitClassesAndMatchingMetadataAndRemovesStaleOutputs() throws Exception {
        Path classes = Files.createDirectories(work.resolve("target/classes/com/openggf/mods/testing")).getParent()
                .getParent().getParent().getParent();
        Files.write(classes.resolve("com/openggf/mods/testing/Kit.class"), new byte[]{1,2,3});
        Files.writeString(classes.resolve("version.properties"), "version=0.7.prerelease\ncommit=test\n");
        Files.createDirectories(classes.resolve("com/openggf/tools/modsdk"));
        Files.write(classes.resolve("com/openggf/tools/modsdk/Cli.class"), new byte[]{9});
        Path output = work.resolve("target/testkit/classes");
        Files.createDirectories(output);
        Files.writeString(output.resolve("stale"), "old");
        ModTestKitPackager.prepare(classes, output);
        assertFalse(Files.exists(output.resolve("stale")));
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(output.resolve("com/openggf/mods/testing/Kit.class")));
        assertEquals(Files.readString(classes.resolve("version.properties")),
                Files.readString(output.resolve("META-INF/openggf-build.properties")));
        assertFalse(Files.exists(output.resolve("com/openggf/tools")));
    }

    @Test void refusesDeletingCompiledInputOrAnythingOutsideBuildRoot() throws Exception {
        Path classes = Files.createDirectories(work.resolve("target/classes"));
        assertThrows(IllegalArgumentException.class, () -> ModTestKitPackager.prepare(classes, classes));
        assertThrows(IllegalArgumentException.class, () -> ModTestKitPackager.prepare(classes, work));
        Path unrelated = Files.createDirectories(work.resolve("outside"));
        Files.writeString(unrelated.resolve("keep"), "user");
        assertThrows(IllegalArgumentException.class, () -> ModTestKitPackager.prepare(classes, unrelated));
        assertEquals("user", Files.readString(unrelated.resolve("keep")));
    }
}
