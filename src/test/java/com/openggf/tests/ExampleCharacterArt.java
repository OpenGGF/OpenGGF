package com.openggf.tests;

import java.nio.file.Files;
import java.nio.file.Path;

/** Builds the example's authored sheets in an owned test directory, without source outputs. */
public final class ExampleCharacterArt {
    private ExampleCharacterArt() { }

    public static Path assemble(Path output) throws Exception {
        Files.createDirectories(output);
        for (String name : new String[]{"mica", "talon", "mica_powered_s2", "mica_powered_s3k"}) {
            String source = name.startsWith("mica_powered_")
                    ? "mica/powered/" + name.substring("mica_powered_".length()) : name + "/sheet";
            Process process = new ProcessBuilder("python3", "tools/modding/playable_art/assemble.py",
                    "examples/mica-and-talon/art/" + source, output.toString()).inheritIO().start();
            if (process.waitFor() != 0) throw new IllegalStateException("Example sheet assembly failed: " + name);
        }
        return output;
    }
}
