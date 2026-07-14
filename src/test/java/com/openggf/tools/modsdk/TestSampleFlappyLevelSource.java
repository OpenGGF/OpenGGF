package com.openggf.tools.modsdk;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the sample-flappy level source materializes and converts through the real CLI. */
class TestSampleFlappyLevelSource {

    @TempDir Path temp;

    @Test
    void flappyLevelSourceConvertsCleanly() throws Exception {
        Path src = Path.of("src/test/resources/mods/sample-flappy-src/project/src/main/mod/level-source");
        Path export = temp.resolve("export");
        Files.createDirectories(export);
        Files.copy(src.resolve("level.json"), export.resolve("level.json"));
        Properties props = new Properties();
        try (var in = Files.newInputStream(src.resolve("binary-assets.properties"))) {
            props.load(in);
        }
        for (String name : props.stringPropertyNames()) {
            Files.write(export.resolve(name), Base64.getDecoder().decode(props.getProperty(name)));
        }
        Path out = temp.resolve("out");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = GgfModCli.run(new String[] {
                "convert", "level", "--from-export", export.toString(), "--out", out.toString()},
                new PrintStream(bytes));
        assertEquals(0, exit, bytes.toString(StandardCharsets.UTF_8));
        assertTrue(Files.exists(out.resolve("level.json")));
    }
}
