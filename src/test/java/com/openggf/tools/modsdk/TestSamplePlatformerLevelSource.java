package com.openggf.tools.modsdk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestSamplePlatformerLevelSource {

    @TempDir Path temp;

    @Test
    void platformerTmxConvertsCleanlyAndDeterministically() throws Exception {
        Path mod = Path.of("src/test/resources/mods/sample-platformer-src/project/src/main/mod");
        Path out1 = temp.resolve("out1");
        Path out2 = temp.resolve("out2");
        for (Path out : new Path[] { out1, out2 }) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int exit = GgfModCli.run(new String[] { "convert", "level",
                    "--from-tmx", mod.resolve("level.tmx").toString(),
                    "--palette", mod.resolve("palette.gpal").toString(),
                    "--out", out.toString() }, new PrintStream(bytes, true, StandardCharsets.UTF_8));
            assertEquals(0, exit, bytes.toString(StandardCharsets.UTF_8));
            assertTrue(Files.exists(out.resolve("level.json")));
        }
        // determinism: byte-identical across runs (mirrors TestTmxLevelImporter's guarantee)
        assertArrayEquals(Files.readAllBytes(out1.resolve("fg-map.bin")),
                Files.readAllBytes(out2.resolve("fg-map.bin")));
    }
}
